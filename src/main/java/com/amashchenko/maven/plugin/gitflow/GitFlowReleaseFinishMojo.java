/*
 * Copyright 2014 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amashchenko.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow release finish mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "release-finish", aggregator = true)
public class GitFlowReleaseFinishMojo extends AbstractGitFlowMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // check uncommitted changes
            checkUncommittedChanges();

            // git for-each-ref --format='%(refname:short)' refs/heads/release/*
            final String releaseBranches = executeGitCommandReturn(
                    "for-each-ref", "--format=\"%(refname:short)\"",
                    "refs/heads/" + gitFlowConfig.getReleaseBranchPrefix()
                            + "*");

            String releaseVersion = null;

            if (StringUtils.isNotBlank(releaseBranches)
                    && StringUtils.countMatches(releaseBranches,
                            gitFlowConfig.getReleaseBranchPrefix()) > 1) {
                throw new MojoFailureException(
                        "More than one release branch exists. Cannot finish release.");
            } else {
                releaseVersion = releaseBranches.trim().substring(
                        releaseBranches.lastIndexOf(gitFlowConfig
                                .getReleaseBranchPrefix())
                                + gitFlowConfig.getReleaseBranchPrefix()
                                        .length());
            }

            if (StringUtils.isBlank(releaseVersion)) {
                throw new MojoFailureException("Release version is blank.");
            }

            // git checkout release/...
            executeGitCommand("checkout", releaseBranches.trim());

            // mvn clean install
            executeMvnCommand("clean", "install");

            // git checkout master
            executeGitCommand("checkout", gitFlowConfig.getProductionBranch());

            // git merge --no-ff release/...
            executeGitCommand("merge", "--no-ff",
                    gitFlowConfig.getReleaseBranchPrefix() + releaseVersion);

            // git tag -a ...
            executeGitCommand("tag", "-a", gitFlowConfig.getVersionTagPrefix()
                    + releaseVersion, "-m", "tagging release");

            // git checkout develop
            executeGitCommand("checkout", gitFlowConfig.getDevelopmentBranch());

            // git merge --no-ff release/...
            executeGitCommand("merge", "--no-ff",
                    gitFlowConfig.getReleaseBranchPrefix() + releaseVersion);

            // get current project version from pom
            String currentVersion = getCurrentProjectVersion();

            String nextSnapshotVersion = null;
            // get next snapshot version
            try {
                DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                        currentVersion);
                nextSnapshotVersion = versionInfo.getNextVersion()
                        .getSnapshotVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            if (StringUtils.isBlank(nextSnapshotVersion)) {
                throw new MojoFailureException(
                        "Next snapshot version is blank.");
            }

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            executeMvnCommand(VERSIONS_MAVEN_PLUGIN + ":set", "-DnewVersion="
                    + nextSnapshotVersion, "-DgenerateBackupPoms=false");

            // git commit -a -m updating poms for ... release
            executeGitCommand("commit", "-a", "-m",
                    "updating poms for next development version");

            // git branch -d release/...
            executeGitCommand("branch", "-d",
                    gitFlowConfig.getReleaseBranchPrefix() + releaseVersion);
        } catch (CommandLineException e) {
            e.printStackTrace();
        }
    }
}