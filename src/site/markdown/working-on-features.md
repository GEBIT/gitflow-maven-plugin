Working on features
-------------------

The workflow when working on feature branches is outlined here:

![working-on-features](./images/working-on-features.png)

### Starting a feature

Typical steps for implementing a feature in a feature branches. To start a new feature branch:

```
mvn flow:feature-start
```

Make sure your local master is not behind the remote, before executing.

To add a Jenkins build job for the feature branch:

If you're on the feature branch:

```
mvn flow:branch-config -DpropertyName=JOB_BUILD -DpropertyValue=true
```
otherwise:

```
mvn flow:branch-config -DbranchName=feature/XYZ-1234 -DpropertyName=JOB_BUILD -DpropertyValue=true
```

(you will need to use the correct branch name, of course).

You can also do this right away when starting the feature branch:

```
mvn flow:feature-start -DjobBuild=true
```

### Working on the feature

You will now start work on the feature branch and commit in this branch.

From time to time you will want to pull in changes from the upstream branch. We recommend rebasing the feature
branch (see [rebase-merge.html]). This will be done by rebasing a feature on the current upstream branch:

```
mvn flow:feature-rebase -N
```

The `-N` option is needed for rare cases where a module in an upstream project is removed, that is still used in the 
current project before the rebase. If you don't specify `-N` yo will get errors about missing dependencies.

*Note:* If multiple people are working on the feature branch, you need to make sure everybody has pushed their changes
before the rebase. After the rebase, everybody will need to fetch and reset the local branch to continue working.


### Preparing to finish

You may want to cleanup the feature branch by rewording commit messages or squashing (merging) multiple commits before
"delivering" it to the upstream development branch. You may even want to do so before a rebase, as if there are 
conflicts with upstream changes it is much easier if you have only a few commits

```
mvn flow:feature-rebase-cleanup -N
```

This will basically just perform a `git rebase --interactive` with the correct options for you, so you need to be
familiar with the [concept in git](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History#_changing_multiple).

In simple cases (just one or two commits) this step will not be necessary!

### Finishing the feature 

Finish the feature by merging (rebasing upon) into the upstream branch:

```
mvn flow:feature-finish
```

As you would normaly do a `flow:feature-rebase` before, there should be no conflicts at this stage.

### If anything goes wrong

It may happen that something goes wrong. We try to make the error message as easy to understand as possible and 
print out which options you have to continue. Please read it carefully!

The complete output of the commands issued in the background is available in a separate logfile (the output contains
a reference to it).
