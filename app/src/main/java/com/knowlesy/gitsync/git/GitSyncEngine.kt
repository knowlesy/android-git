package com.knowlesy.gitsync.git

import android.content.Context
import com.knowlesy.gitsync.model.SyncConfig
import com.knowlesy.gitsync.model.SyncLogger
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.HttpTransport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
import android.media.MediaScannerConnection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncResult(
    val success: Boolean,
    val status: String, // "SUCCESS", "ERROR", "CONFLICT"
    val message: String,
    val changedFiles: List<String> = emptyList()
)

class GitSyncEngine(private val context: Context) {
    private val config = SyncConfig(context)
    private val logger = SyncLogger(context)

    init {
        // Ensure JGit uses JDK's standard HTTPS stack which supports standard TLS/SSL on Android
        try {
            HttpTransport.setConnectionFactory(JDKHttpConnectionFactory())

            // Configure WindowCache for resource-constrained Android devices to prevent GC/Inflater closure
            val cacheConfig = org.eclipse.jgit.storage.file.WindowCacheConfig().apply {
                packedGitLimit = 32 * 1024 * 1024 // 32 MB
                packedGitWindowSize = 16 * 1024 // 16 KB
                packedGitOpenFiles = 128
                isPackedGitMMAP = false // Disable MMAP for Android stability
            }
            cacheConfig.install()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val syncLock = Any()
    }

    fun executeSync(): SyncResult {
        synchronized(syncLock) {
            if (!config.isValid()) {
                val errMsg = "Sync configuration is incomplete."
                logger.logError(errMsg)
                return SyncResult(false, "ERROR", errMsg)
            }

            var git: Git? = null
            try {
            val localDir = File(config.syncFolder)
            if (!localDir.exists()) {
                val created = localDir.mkdirs()
                if (!created) {
                    throw Exception("Could not create local sync folder: ${localDir.absolutePath}")
                }
            }

            val isGitRepo = File(localDir, ".git").exists()
            val credentials = UsernamePasswordCredentialsProvider(config.username, config.patToken)

            // Detect if clone previously failed leaving a broken/empty .git folder
            val contents = localDir.listFiles() ?: emptyArray()
            val nonGitContents = contents.filter { it.name != ".git" }
            
            var isBrokenClone = false
            if (isGitRepo && nonGitContents.isEmpty()) {
                try {
                    Git.open(localDir).use { g ->
                        isBrokenClone = g.repository.resolve("HEAD") == null
                    }
                } catch (e: Exception) {
                    isBrokenClone = true
                }
            }

            val actualGitRepo = isGitRepo && !isBrokenClone

            if (!actualGitRepo) {
                if (isGitRepo) {
                    // Clean up broken git folder so we can clone freshly
                    File(localDir, ".git").deleteRecursively()
                }

                // Check if directory has actual user files (not starting with .)
                val hasUserFiles = localDir.listFiles()?.any { !it.name.startsWith(".") } ?: false

                if (!hasUserFiles) {
                    // Scenario B: No user files. We can do a clean clone-and-overlay.
                    val tempCloneDir = File(context.cacheDir, "temp_clone_${System.currentTimeMillis()}")
                    if (tempCloneDir.exists()) tempCloneDir.deleteRecursively()
                    tempCloneDir.mkdirs()

                    try {
                        android.util.Log.d("GitSyncDebug", "Starting clean clone into temp directory...")
                        Git.cloneRepository()
                            .setURI(config.gitUrl)
                            .setDirectory(tempCloneDir)
                            .setCredentialsProvider(credentials)
                            .setCloneAllBranches(true)
                            .setBranch("refs/heads/main")
                            .call()
                            .close()

                        android.util.Log.d("GitSyncDebug", "Clone successful. Overlaying config files...")
                        
                        // Overlay existing config/dot files from localDir to tempCloneDir
                        localDir.listFiles()?.forEach { file ->
                            if (file.name != ".git") {
                                val dest = File(tempCloneDir, file.name)
                                file.copyRecursively(dest, overwrite = true)
                            }
                        }

                        // Clear localDir completely
                        localDir.listFiles()?.forEach { it.deleteRecursively() }

                        android.util.Log.d("GitSyncDebug", "Moving cloned repository contents to local sync folder...")
                        // Move tempCloneDir contents to localDir
                        moveDirContents(tempCloneDir, localDir)
                        tempCloneDir.deleteRecursively()

                        scanFilesRecursively(context, localDir)
                        val msg = "Cloned remote repository successfully (with local configs preserved)."
                        logger.logSuccess(msg)
                        return SyncResult(true, "SUCCESS", msg)

                    } catch (e: Exception) {
                        android.util.Log.e("GitSyncDebug", "Clone-and-overlay failed", e)
                        tempCloneDir.deleteRecursively()
                        throw e
                    }
                } else {
                    // Scenario A: Local directory has files, initialize local Git repository first
                    Git.init().setDirectory(localDir).call().use { inited ->
                        val repoConfig = inited.repository.config
                        repoConfig.setString("remote", "origin", "url", config.gitUrl)
                        repoConfig.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                        repoConfig.save()
                    }

                    // Open and commit existing local files
                    Git.open(localDir).use { g ->
                        g.add().addFilepattern(".").call()
                        val status = g.status().call()
                        if (status.hasUncommittedChanges() || status.untracked.isNotEmpty()) {
                            g.commit()
                                .setAuthor(config.username, config.email)
                                .setCommitter(config.username, config.email)
                                .setMessage("Initial local commit of existing files")
                                .call()
                        }
                    }
                }
            }

            // Open the repo for the pull/merge/push sync cycle
            git = Git.open(localDir)
            val repo = git.repository
            val branch = repo.branch ?: "main"

            // Self-healing check: if working directory has only .git but HEAD exists, do a hard checkout
            val currentContents = localDir.listFiles() ?: emptyArray()
            val currentNonGit = currentContents.filter { it.name != ".git" }
            if (currentNonGit.isEmpty() && repo.resolve("HEAD") != null) {
                git.reset().setMode(ResetCommand.ResetType.HARD).call()
            }

            // 1. Stage and commit local changes
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call() // Track deletions
            val status = git.status().call()

            val changedFilesList = mutableListOf<String>()
            changedFilesList.addAll(status.modified)
            changedFilesList.addAll(status.added)
            changedFilesList.addAll(status.removed)
            changedFilesList.addAll(status.untracked)

            if (changedFilesList.isNotEmpty()) {
                val commitMsg = "GitSync: Local changes at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
                git.commit()
                    .setAuthor(config.username, config.email)
                    .setCommitter(config.username, config.email)
                    .setMessage(commitMsg)
                    .call()
            }

            // 2. Fetch remote changes
            git.fetch()
                .setCredentialsProvider(credentials)
                .call()

            // 3. Merge remote changes into local branch
            val remoteRef = repo.resolve("refs/remotes/origin/$branch")
            var conflicted = false
            val mergedFiles = mutableListOf<String>()

            if (remoteRef != null) {
                val mergeResult = git.merge()
                    .include(remoteRef)
                    .call()

                when (mergeResult.mergeStatus) {
                    MergeResult.MergeStatus.CONFLICTING -> {
                        conflicted = true
                        handleMergeConflicts(repo, git, mergeResult.conflicts.keys)
                    }
                    MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> {
                        // Nothing to merge
                    }
                    else -> {
                        if (mergeResult.mergeStatus.isSuccessful) {
                            // Merge succeeded without conflicts, track changes
                            mergeResult.mergedCommits?.forEach { commit ->
                                // Track files changed in merged commits (optional)
                            }
                        }
                    }
                }
            }

            // 4. Push local changes to remote
            git.push()
                .setCredentialsProvider(credentials)
                .call()

            val resultMsg = if (conflicted) {
                "Sync completed with conflicts resolved (${config.conflictStrategy})"
            } else {
                "Sync successful."
            }

            // Run Media Scanner to ensure files immediately index in Android Files/MediaStore
            scanFilesRecursively(context, localDir)

            if (conflicted) {
                logger.logConflict(resultMsg, changedFilesList)
                return SyncResult(true, "CONFLICT", resultMsg, changedFilesList)
            } else {
                logger.logSuccess(resultMsg, changedFilesList)
                return SyncResult(true, "SUCCESS", resultMsg, changedFilesList)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: e.toString()
            logger.logError("Sync failed: $errorMsg")
            return SyncResult(false, "ERROR", errorMsg)
        } finally {
            git?.close()
        }
      }
    }

    private fun handleMergeConflicts(repo: Repository, git: Git, conflictPaths: Set<String>) {
        when (config.conflictStrategy) {
            "KEEP_OURS" -> {
                for (path in conflictPaths) {
                    git.checkout().addPath(path).setStage(CheckoutCommand.Stage.OURS).call()
                    git.add().addFilepattern(path).call()
                }
                git.commit()
                    .setAuthor(config.username, config.email)
                    .setCommitter(config.username, config.email)
                    .setMessage("GitSync: Resolved conflicts by keeping local changes")
                    .call()
            }
            "KEEP_THEIRS" -> {
                for (path in conflictPaths) {
                    git.checkout().addPath(path).setStage(CheckoutCommand.Stage.THEIRS).call()
                    git.add().addFilepattern(path).call()
                }
                git.commit()
                    .setAuthor(config.username, config.email)
                    .setCommitter(config.username, config.email)
                    .setMessage("GitSync: Resolved conflicts by keeping remote changes")
                    .call()
            }
            else -> {
                // "CONFLICT_COPY" (Default)
                val dirCache = repo.readDirCache()
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

                for (path in conflictPaths) {
                    val oursEntry = getEntryForStage(dirCache, path, 2) // Stage 2 is ours
                    val theirsEntry = getEntryForStage(dirCache, path, 3) // Stage 3 is theirs

                    val localFile = File(repo.workTree, path)
                    
                    // Extract ours (local) content
                    val oursContent = if (oursEntry != null) {
                        repo.open(oursEntry.objectId).bytes
                    } else null

                    // Extract theirs (remote) content
                    val theirsContent = if (theirsEntry != null) {
                        repo.open(theirsEntry.objectId).bytes
                    } else null

                    // Step A: Restore ours (local) to the main file path
                    if (oursContent != null) {
                        localFile.writeBytes(oursContent)
                    } else {
                        if (localFile.exists()) localFile.delete()
                    }

                    // Step B: Write theirs (remote) to a conflict copy file
                    if (theirsContent != null) {
                        val extension = localFile.extension
                        val nameWithoutExt = localFile.nameWithoutExtension
                        val parentDir = localFile.parentFile ?: localFile
                        val conflictFileName = if (extension.isNotEmpty()) {
                            "$nameWithoutExt.conflict-$timestamp.$extension"
                        } else {
                            "$nameWithoutExt.conflict-$timestamp"
                        }
                        
                        val conflictFile = File(parentDir, conflictFileName)
                        conflictFile.writeBytes(theirsContent)

                        // Stage the conflict copy
                        val relativeConflictPath = conflictFile.relativeTo(repo.workTree).path
                        git.add().addFilepattern(relativeConflictPath).call()
                    }

                    // Step C: Stage the main file (marking conflict as resolved)
                    if (localFile.exists()) {
                        git.add().addFilepattern(path).call()
                    } else {
                        git.rm().addFilepattern(path).call()
                    }
                }

                git.commit()
                    .setAuthor(config.username, config.email)
                    .setCommitter(config.username, config.email)
                    .setMessage("GitSync: Resolved conflicts by creating conflict copies")
                    .call()
            }
        }
    }

    private fun scanFilesRecursively(context: Context, directory: File) {
        try {
            val paths = mutableListOf<String>()
            directory.walkTopDown().forEach { file ->
                // Ignore hidden files/directories (like .git, .obsidian-mobile)
                if (!file.name.startsWith(".") && file.isFile) {
                    paths.add(file.absolutePath)
                }
            }
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    paths.toTypedArray(),
                    null
                ) { _, _ -> }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getEntryForStage(dirCache: DirCache, path: String, stage: Int): DirCacheEntry? {
        for (i in 0 until dirCache.entryCount) {
            val entry = dirCache.getEntry(i)
            if (entry.pathString == path && entry.stage == stage) {
                return entry
            }
        }
        return null
    }

    private fun moveDirContents(src: File, dest: File) {
        src.listFiles()?.forEach { file ->
            val target = File(dest, file.name)
            if (file.isDirectory) {
                target.mkdirs()
                moveDirContents(file, target)
                file.delete()
            } else {
                if (!file.renameTo(target)) {
                    file.copyTo(target, overwrite = true)
                    file.delete()
                }
            }
        }
    }
}

