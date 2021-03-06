package com.faforever.client.patch;

import com.faforever.client.task.ResourceLocks;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

import static com.github.nocatch.NoCatch.noCatch;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

@Lazy
@Component
public class JGitWrapper implements GitWrapper {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void clone(String repositoryUri, Path targetDirectory, ProgressMonitor progressMonitor) {
    ResourceLocks.acquireDownloadLock();
    ResourceLocks.acquireDiskLock();
    try {
      logger.debug("Cloning {} into {}", repositoryUri, targetDirectory);
      noCatch(() -> cloneRepository()
          .setProgressMonitor(progressMonitor)
          .setURI(repositoryUri)
          .setDirectory(targetDirectory.toFile())
          .call());
    } finally {
      ResourceLocks.freeDiskLock();
      ResourceLocks.freeDownloadLock();
    }
  }

  @Override
  public void fetch(Path repoDirectory, PropertiesProgressMonitor progressMonitor) {
    ResourceLocks.acquireDownloadLock();
    ResourceLocks.acquireDiskLock();
    try {
      logger.debug("Fetching into {}", repoDirectory);
      noCatch(() -> open(repoDirectory.toFile())
          .fetch()
          .setProgressMonitor(progressMonitor)
          .call());
    } finally {
      ResourceLocks.freeDiskLock();
      ResourceLocks.freeDownloadLock();
    }
  }

  @Override
  public void checkoutRef(Path repoDirectory, String ref) {
    ResourceLocks.acquireDiskLock();
    try {
      noCatch(() -> open(repoDirectory.toFile())
          .checkout()
          .setForce(true)
          .setName(ref)
          .call());
    } finally {
      ResourceLocks.freeDiskLock();
    }
  }
}
