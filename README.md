# Automated Forward Merging and Notification System

## Overview
We need a system to automate the tracking of changes across release branches, notify users who have submitted changes in these branches, and ensure that necessary changes are carried forward into subsequent branches, including the master branch. The system should determine the "happy path" for forward merging, either by chaining forward or merging directly to master, with a clear, documented opinion on the chosen strategy.

## Context
Release branches are created for each module consumed by the monolith, with the goal of producing stable, secure artifacts for each module that can be updated independently. These artifacts are included in the Bill Of Materials (BOM) associated with each quarterly release. The system must ensure that fixes applied to any release branch are automatically considered for integration into subsequent branches, including master. Users who submit changes should be notified of the status of their changes and any required forward merges.

## Proposed Solution: Git Workflow Assumption
This system will follow a similar workflow to the existing Perforce-based solution but will be adapted for a Git-based distributed version control system (VCS).

### Workflow
Identify Git Projects with Release Branches:

* Collect and maintain a list of Git projects that have release branches. This can be done using a configuration file or by querying the Git repository.
Isolate Release Branches for Specific Projects:

* For each project, identify the relevant release branches. This will typically involve querying the branch structure and filtering based on naming conventions or tags.
Get Commits Prior to the Previous Branch Cut:

* For each release branch, retrieve the list of commits that were made after the last branch cut (e.g., from the point of the last release).
### Filter Out Integrated Changes:

* Use `git cherry -v $sourceBranch $destBranch` to compare the source branch with the destination branch and filter out changes that have already been integrated.
### Filter Out Excluded Changes:

Exclude changes marked for null-integration or those that have already been merged. This can be done using Git notes or parsing commit messages for specific tags (e.g., [null-integrate]).
### Create Patch for Pending Merges:

For each change that has not been integrated or excluded, create a patch or a set of pending changes that need to be merged into the destination branch.
### Email Patches to Users:

Use git send-email to notify each user responsible for the changes, providing them with the necessary patches and instructions for applying them to the release branch.
### Record Exclusion Information:

Use Git notes to record any exclusion decisions, ensuring that these are carried forward and respected in future merge decisions.
## Discussion
### Git Hooks:

Git hooks can be used to automate the triggering of scripts for tasks such as checking for pending merges, generating patches, and sending emails.
### Git Notes:

Git notes are useful for recording metadata about commits, such as exclusion information, without altering the commits themselves.
### Automated Emails:

The git send-email tool can be configured to send automated notifications to users, reducing the manual overhead.
### Chaining Forward vs. Merging to Master:

The system should decide on a clear "happy path" for merging. This could involve chaining fixes forward through all relevant branches or directly merging them to the master branch. Prototyping both approaches will help in making an informed decision.
