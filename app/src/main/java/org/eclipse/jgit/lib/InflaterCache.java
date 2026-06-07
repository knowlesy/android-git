package org.eclipse.jgit.lib;

import java.util.zip.Inflater;

public class InflaterCache {
	private static final int SZ = 4;

	private static final Inflater[] inflaterCache = new Inflater[SZ];

	public static Inflater get() {
		synchronized (inflaterCache) {
			for (int i = 0; i < SZ; i++) {
				Inflater r = inflaterCache[i];
				if (r != null) {
					inflaterCache[i] = null;
					return r;
				}
			}
		}
		return new SafeInflater();
	}

	public static void release(Inflater r) {
		if (r == null)
			return;
		if (r instanceof SafeInflater) {
			try {
				r.reset();
			} catch (IllegalStateException e) {
				return;
			}
			synchronized (inflaterCache) {
				for (int i = 0; i < SZ; i++) {
					if (inflaterCache[i] == null) {
						inflaterCache[i] = r;
						return;
					}
				}
			}
		}
		try {
			r.end();
		} catch (Exception e) {
			// Ignore
		}
	}

	private static class SafeInflater extends Inflater {
		@Override
		public void end() {
			// Do nothing to prevent Android's InflaterInputStream from closing it
		}
	}
}
