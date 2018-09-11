# Merge vs. Rebase for feature branches

With gitflow-maven-plugin you can use both rebase and merge based workflow for feature branches. The difference is what
to do to pull in changes from the upstream branch (typically the development branch "master").

## Rebase based workflow

In a rebase based workflow you use `mvn flow:feature-rebase -N` to update your feature branch. At this point the current
commit from your upstream branch is taken and all your changes are rebased on it. There are extra steps to make sure
any modules newly introduced on the upstream branch are also adapted to the feature branch version.

If there are multiple developers working with the feature branch, everybody will have to push the current changes and
stop working while the rebase is in process.

If makes sense to cleanup the commits in the feature branch before rebasing, as with a lower number of commits there
will be less problems when rebasing. Use `mvn flow:feature-cleanup` to do so.

It is possible that an automatic rebase is not pssible. One of the main reasons are modification in the `pom.xml` of a
module in the upstream branch. But also other conflicting modifications may result in a (temporary) abort of the rebase
operation.

In this case you will need to manually resolve the conflict:
* Use `git status' to get an overview which files do have conflicts
* Open the conflicting files in an editor and resolve the conflict (look for the typical `<<<<<<` markers!)
* Add the change to the index using `git add XXXX`
* Continue by just issuing the rebase command again!

When all the conflicts are resolved, the modified branch is pushed to the repository. Now everybody can updatee the
feature branch, e.g.

```
git checkout feature/XY-1234
git pull -f
```

The history will now look like the feature branch has originally started from the current commit in the upstream branch.
In this state you are ready to finish your feature.

Rebasing prevents haven a "subway-map-like-history". Also, a conflict once resolved will never again make any problems.
You can even later rebase a feature again on another branch, which is handy for backports.

## Merge based workflow

In a merge based workflow you would merge the upstream branch into the feature branch from time to time. Every such
merge will create a new merge commit. Any changes neccessary for the merge will be attributed to this commit.

After merging you are ready to finish a feature. Also, all your team members will be able to simply use `git pull` to
update the feature branch.

However, there are a lot of downsides to using merges:
* When a branch has merge commits, you cannot rebase anymore. This means you cannot edit the commit messages or clean up your branch before it is merged into production
* The history becomes very messed up (think of London Tube Map)
* Adjustments needed due to changes in the master are done in the merge commits, so the "real" commits introducing a change are retrospectively wrong and would not apply
* You cannot easily apply the series of commits into another branch (to backport something)
* Reverting merges may have unintended side effects

# Use rebase whenever possible

For this reason we *strongly* advise to use only rebases and never merges. The only exceptin is an automatic merge
commit which is added when finishing a branch (see `git merge --no-ff`). This commit is designed to mark the commits
of a feature branch as belonging together. This merge commit will not have any changes itself, as one parent is the
commit of the upstream branch the feature branch is based upon (so only one side has commits).
This will make it ease to later cherry pick all these changes to another
maintenance branch.