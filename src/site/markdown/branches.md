# Branching model

There are several types of branches used:

## development

The development branch is typically called `master`. All development and fixing is typically targetted at this branch,
i.e. all feature branches are started from `master`.

If you follow this recommendation, it will ensure no change and no fix will ever get lost.

There are situations, where you will develop off a maintenance branch. In theses cases you will need to take extra care
to include all those changes also in the development branch!

## maintenance

Typically your project will not be simply linear in it's version history. Instead, you will have multiple versions
that are under maintenance. Every version under maintenance has a maintenance branch.

These branches are stored as `maintenance/[version]`.

## integration

Integration branches are a special case, as they are only ever updated automatically. They will be typically behind
the branch they are following and are either updated on release, or on successfull integration builds. As such they
are a very good candidate for basing feature branches upon (as they pass all tests).

The branches are stored as `latest` (for the development branch) and `latest-[baseversion]` (e.g. `latest-maintenance/2.1`)
for maintenance branches.

## feature

The normal branch to work on is a feature branch. It is typically started off the development branch, but can also
be started off a maintenance branch (don't forget to include it in the development brach after finishing, then!).

Features will receive a feature specific version number, which will avoid conflichts when switching features. Also, it
enables to work with just some projects open, as long as either the feature was built and published to Nexus or at least
built and installed locally.

The branches are stored as `feature/[issue]`.

**Feature branch for a maintenance branch:**

`mvn flow:feature-start` can also create a feature branch starting from a maintenance branch. In that case use a
`-baseline-X-Y` suffix for the branch name to make the origin of the branch obvious. The mvn flow:feature-start will
create a branch `feature/POS-123-baseline-2-3`, though the suffix will not be included in the version in the pom.xml:
`2.3.0-POS-123-SNAPSHOT`.

## epic

In larger projects single big features are divided into smaller features, that should be developed together. Only after
all features are finished and integrated, the whole result is then integrated into the upstream branch.

The branch where all those features are collected is called an *Epic* branch. Features are started off it and may
be updated from time to time from this branch. As there are multiple features, the epic branch itself must never be
rebased, at least until the last feature is finished. Then, you *may* rebase the branch, but you will typically have
merge commit in it which requires manual conflict resolution. Still, it may be worth the time as a final step to cleanup
and commit to the "eternal" history.

Please not that feature branches related to the epic will not include the epic version number in it's own number. It's
assumed the Epic itself has an own issue in JIRA to track it.

The branches are stored as `epic/[version]`.

## release

Release branches are temporary while a release is performed. Typically they will be started and deleted automatically
while a release build runs. But if a release is performed manually (using `mvn flow:releast-start` and
`mvn flow:releast-finish`) they may live a bit longer.

Be careful, as any commit introduced after starting a release will be merged after the release is finished automatically.

Thes branches are stored as `release/[version]`.

The Maven plugin for Vincent Driessen's [successful Git branching model](http://nvie.com/posts/a-successful-git-branching-model/).

We use a modified branching model with some specialities:
* Support for maintenance branches
* Support for epic branches
* Automatic version changes for feature branches

You perform command by invoking goals like this:
```
mvn flow:[goal] -N
```

## hotfixes

Hotfixes are handled just like "normal" maintenance branches, just on demand.

* You branch off the tagged release, e.g. 1.2.3 to create the branch `maintenance/[project]-1.2.3` (i.e. with a digit more than usual).
* The version on this branch is set to have an additional component, e.g. 1.2.3.1-SNAPSHOT in the sample above
* A release produces the hotfix, e.g. 1.2.3.1
* A next hotfix on this branch would then be 1.2.3.2
* With the next release of the maintenance branch the hotfix branch can be removed (the tagged releases remain, of course)


## user branches

There is one more type of banches, which has no special support, but which is commonly used. For experiments every user
can create a user specific branch. The branch can have any content, typically you use it by explicitly (foce)
pushing to that branch. You may even create a buildjob for it, but you should make sure to not deploy the results to nexus.

These branches are stored as `user/[username]`.
