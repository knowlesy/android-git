package com.example.gitsync.git

import android.content.Context
import com.example.gitsync.model.SyncConfig
import com.example.gitsync.model.SyncLogger
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.HttpTransport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun executeSync(): SyncResult {
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

            if (!isGitRepo) {
                // If local directory has no files, perform a clean clone
                val files = localDir.list()
                if (files.isNullOrEmpty()) {
                    Git.cloneRepository()
                        .setURI(config.gitUrl)
                        .setDirectory(localDir)
                        .setCredentialsProvider(credentials)
                        .call()
                        .use { cl ->
                            val msg = "Cloned remote repository successfully."
                            logger.logSuccess(msg)
                            return SyncResult(true, "SUCCESS", msg)
                        }
                } else {
                    // Local directory has files, initialize local Git repository first
                    Git.init().setDirectory(localDir).call().use { inited ->
                        val repoConfig = inited.repository.config
                        repoConfig.setString("remote", "origin", "url", config.gitUrl)
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

            if (conflicted) {
                logger.logConflict(resultMsg, changedFilesList)
                return SyncResult(true, "CONFLICT", resultMsg, changedFilesList)
            } else {
                logger.logSuccess(resultMsg, changedFilesList)
                return SyncResult(true, "SUCCESS", resultMsg, changedFilesList)
            }

        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            logger.logError("Sync failed: $errorMsg")
            return SyncResult(false, "ERROR", errorMsg)
        } finally {
            git?.close()
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

    private fun getEntryForStage(dirCache: DirCache, path: String, stage: Int): DirCacheEntry? {
        for (i in 0 until dirCache.entryCount) {
            val entry = dirCache.getEntry(i)
            if (entry.pathString == path && entry.stage == stage) {
                return entry
            }
        }
        return null
    }
}
