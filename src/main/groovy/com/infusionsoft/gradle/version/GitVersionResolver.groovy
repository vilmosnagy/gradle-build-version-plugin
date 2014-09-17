package com.infusionsoft.gradle.version

import static com.google.common.collect.Multimaps.*

import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.base.Supplier
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.gradle.api.GradleScriptException

import java.util.regex.Matcher
import java.util.regex.Pattern

class GitVersionResolver {

    public static final String HEAD = 'HEAD'

    private final String versionSplitter
    private final String releaseTagPattern
    private final String matchGroup
    private final String releaseSuffix
    private final String snapshotSuffix
    private final String releaseTagIfNone
    private final Pattern compiledPattern

    final Multimap<ObjectId, String> namedCommits
    final Repository repo


    GitVersionResolver(Repository repo, TagOptions tagSomething) throws IOException, GitAPIException {
        Preconditions.checkNotNull(repo)
        Preconditions.checkNotNull(tagSomething)
        versionSplitter = tagSomething.versionSplitter
        releaseTagPattern = tagSomething.releaseTagPattern
        matchGroup = tagSomething.groupMatcher
        releaseSuffix = tagSomething.releaseSuffix
        snapshotSuffix = tagSomething.snapshotSuffix
        releaseTagIfNone = tagSomething.releaseTagIfNone
        compiledPattern = Pattern.compile(releaseTagPattern)

        this.repo = repo
        namedCommits = mapOfCommits()
    }

    String getVersion(boolean isRelease) throws IOException, GitAPIException {
        if (isRelease) {
            newReleaseUseCase()
        } else if (isHeadTaggedAsRelease()) {
            existingReleaseUseCase()
        } else {
            developerUseCase()
        }
    }

    private class VersionDescription {
        private final String version
        private final String tagName

        VersionDescription(String version, String tagName) {
            this.version = version
            this.tagName = tagName
        }
    }

    private VersionDescription plusOneUseCase() throws IOException, GitAPIException {
        String releaseTag = findMostRecentTagMatch()

        String version = releaseTag.replaceAll(releaseTagPattern, matchGroup)


        List<String> versionElement = Lists.newArrayList(Splitter.on(versionSplitter).split(version))

        Integer lastElement = Integer.parseInt(versionElement.remove(versionElement.size() - 1))
        versionElement.add(String.valueOf(lastElement + 1))
        String incrementedVersion = Joiner.on(versionSplitter).join(versionElement)

        String incrementedReleaseTag = releaseTag.replaceAll(releaseTagPattern) { fullTag, versionGroup ->
            fullTag.replaceAll(versionGroup, incrementedVersion)
        }

        new VersionDescription(incrementedVersion, incrementedReleaseTag)
    }

    private String developerUseCase() throws IOException, GitAPIException {
        plusOneUseCase().version + snapshotSuffix
    }

    private String newReleaseUseCase() throws IOException, GitAPIException {
        VersionDescription versionDescription = plusOneUseCase()
        tagRepo(versionDescription.tagName)
        versionDescription.version + releaseSuffix
    }

    private String existingReleaseUseCase() throws IOException, GitAPIException {
        String releaseTag = findMostRecentTagMatch()
        releaseTag.replaceAll(releaseTagPattern, matchGroup) + releaseSuffix
    }

    private boolean isReleaseTag(String tagCandidate) {
        final Matcher releaseTagMatcher = compiledPattern.matcher(tagCandidate)
        releaseTagMatcher.matches()
    }

    private void tagRepo(String tagName) throws GitAPIException {
        if (isHeadTaggedAsRelease()) {
            throw new GradleScriptException(
                    'A commit should have at most a single release version', new IllegalStateException())
        } else {
            final Git git = new Git(repo)
            git.tag().setName(tagName).call()
        }
    }

    private boolean isHeadTaggedAsRelease() throws IOException {
        ObjectId head = repo.resolve(HEAD)
        namedCommits.get(head).any {
            isReleaseTag(it)
        }
    }

    private String findMostRecentTagMatch() throws IOException, GitAPIException {
        Pattern compiledPattern = Pattern.compile(releaseTagPattern)

        ObjectId head = repo.resolve(HEAD)
        if (head == null) {
            if (isReleaseTag(releaseTagIfNone)) {
                return releaseTagIfNone
            }
            throw new GradleScriptException(
                    'Unable to find a release tag to base our version on ' +
                            'and no valid default release tag has been supplied', new IllegalArgumentException())
        }
        RevWalk walk = new RevWalk(repo)
        RevCommit headCommit = walk.parseCommit(head)

        walk.markStart(headCommit)
        try {
            for (RevCommit commit : walk) {
                final Set<String> matchingTags = new HashSet<String>()
                final Collection<String> objectTags = namedCommits.get(commit.id)

                if (objectTags != null && objectTags.size() > 0) {
                    for (String tagName : objectTags) {
                        final Matcher releaseTagMatcher = compiledPattern.matcher(tagName)
                        if (releaseTagMatcher.matches()) {
                            matchingTags.add(tagName)
                        }
                    }
                }

                if (matchingTags.size() > 0) {
                    if (matchingTags.size() > 1) {
                        throw new GradleScriptException(
                                'A commit should have at most a single release version', new IllegalStateException())
                    }
                    return matchingTags.iterator().next()
                }

            }
        } finally {
            walk.release()
        }
        if (isReleaseTag(releaseTagIfNone)) {
            return releaseTagIfNone
        }
        throw new GradleScriptException(
                'Unable to find a release tag to base our version on ' +
                        'and no valid default release tag has been supplied', new IllegalArgumentException())

    }

    // map of target objects and the tags they have.
    private Multimap<ObjectId, String> mapOfCommits() throws IOException, GitAPIException {
        final Map<ObjectId, Collection<String>> namedCommits = Maps.newHashMap()
        final Multimap<ObjectId, String> commits = newListMultimap(
                namedCommits,
                new Supplier<List<String>>() {
                    @Override
                    List<String> get() {
                        Lists.newArrayList()
                    }
                }
        )

        final ObjectId head = repo.resolve(HEAD)
        if (head == null) {
            return commits
        }
        final RevWalk walk = new RevWalk(repo)
        final RevCommit headCommit = walk.parseCommit(head)

        walk.markStart(headCommit)

        for (Ref tagRef : new Git(repo).tagList().call()) {
            walk.reset()

            String revTagName = null
            ObjectId targetObjectId = null
            ObjectId objectId = repo.resolve(tagRef.name)

            try {
                RevTag revTag = walk.parseTag(objectId)
                if (revTag != null) {
                    targetObjectId = revTag.object.id
                    revTagName = revTag.tagName
                }
            } catch (IncorrectObjectTypeException ignored) {
            }

            if (targetObjectId != null) {
                commits.put(targetObjectId, revTagName)
            }
        }

        walk.release()

        commits
    }
}