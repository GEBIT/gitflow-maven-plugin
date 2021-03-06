When do I start a maintenance branch?
-------------------------------------

Maintenance branches are always started on demand only using

```
mvn flow:maintenance-start
```

Normally these are started after a release happened, but you may also start them beforehand to decouple the release from the development branch (see next question). Alas, this should not happen too soon, as you will need to pick too many changes to the release branch otherwise.


# How do I perform a hotfix release ?

You start a new maintenance branch off of an existing version tag:

```
mvn flow:maintenance-start
...
[INFO] --- gitflow-maven-plugin:2.0.2:maintenance-start (default-cli) @ trend-project-parent ---
[INFO] Starting maintenance start process
Release:
0. <current commit>
...
T. <prompt for explicit tag name>
Choose release to create the maintenance branch from or enter a custom tag or release name (0/.../T):
```
Here you choose `T` and enter the release tag
```
Enter explicit tag name: myproject-x.y.z
...
```

This will produce a buildjob in Jenkins, which you can then use to release off this hotfix branch


I want a release `x.y` next week, but I don't want any unwanted changes to sneak in.
------------------------------------------------------------------------------------

You would start by creating a release/maintenance branch off the current development branch:
* Checkout your development branch:

```
git checkout master
```

* Start the maintenance branch:

```
mvn flow:maintenance-start
```

And select option `0: current commit`

This will create a `maintenance/x.y` branch, keeping the current version (probably: `x.y-SNAPSHOT`)

Now you increment the version number on the development branch:

```
git checkout master
mvn flow:set-version
```

And set the next version to `x.(y+1)-SNAPSHOT`

The release itself will come from the `maintenance/x.y` branch. When you're ready release it as `x.y.0`.

We do not know if a release is actually THE release, because QA takes some time.
--------------------------------------------------------------------------------

When a release is produced, that version number is "burned" (otherwise you would never be sure which version you actually mean). So in this case you need to produce a release which might not be the final one. You may use [build promotion](https://jira.gebit.de/browse/GBLD-215) to archive the release in Nexus, though.

* You should use a pre-created maintenance branch, even for bugfix releases (e.g. `maintenance/x.y.z`)
* You create the release `x.y.z.0` when you're ready, the next development version is then `x.y.z.1-SNAPSHOT`
* If you need to respin the release, produce the `x.y.z.1` release, the next development version is then `x.y.z.2-SNAPSHOT`
* When the final release is accepted, make sure it is published ([GBLD-215](https://jira.gebit.de/browse/GBLD-215))
* Any hotfix is produced on the same branch, i.e. if your release is `x.y.z.r`, the first hotfix is `x.y.z.(r+1)`

I want to mix integration builds and releases in some uniform and monotonous version numbering, e.g. for deployment
-------------------------------------------------------------------------------------------------------------------

As integration builds and releases are produced from the same buildjob, you can use the build number for a uniform an monotonous versioning. For this we provide a Paramater in the Buildjob called `BUILD_DEPLOY_VERSION` (see [GBLD-218](https://jira.gebit.de/browse/GBLD-218)).

* First of all, determine the number of version components in use. This depends on your release process and will either be 3 or 4 components. Plan one additional component for the build number, so the default length is `5`
* Integration builds will be numbered `x.y.z.0.b`, where `b` is the build number. This will be produced from the version number `x.y.z.0-Ib`, by stripping any `\-Ixxx` qualifier and using the `BUILD_NUMBER` build parameter
* Release builds will be numbered the same, just without the stripping (there is no qualifier used)
* The 4th version component is the hotfix number

Example:

* You want to release `1.2.3`
* You already produced a candidate release `1.2.3` with build `43` yielding a deployment version of `1.2.3.0.43`
* The development version is incremented to `1.2.3.1-SNAPSHOT`
* This candidate was not accepted.
* You produced an integration build `1.2.3.1-I44` yielding `1.2.3.1.44` after the first release candidate
* Now, with build `45` you produce the second candidate release as `1.2.3.1.45`.
	* The development version is incremented to `1.2.3.2-SNAPSHOT`
* The next integration build would be `1.2.3.2-I46`
* The second candidate was accepted, so you publish it in nexus ([GBLD-215](https://jira.gebit.de/browse/GBLD-215))
* After a week, a hotfix is necessary.
	* No integration build has run yet
	* You directly produce it as `1.2.3.2` yielding `1.2.3.2.46` for deployment

How to rebase a feature branch manually?
----------------------------------------

In very seldom cases it is needed to rebase a feature branch without using `mvn flow:feature-rebase`. Because of feature specific versions (e.g. `1.2.3-XYZ-42-SNAPSHOT`) and corresponding version change commit it is mostly not enough to use only `git rebase`.

Following steps ensure that the feature will be properly rebased even if the project version was changed on base branch.

**NOTE:** New or removed child projects on feature branch or on base branch are not covered by this instructions!

* Ensure that there are no uncommitted changes and base branch (`master` or a maintenance branch) is up to date.
* Create and checkout a temporary feature branch based on the base branch (e.g. on `master`)
	```
	git checkout -b tmp-feature/XYZ-42 master
	```
* Update the project version to the new feature specific version. You have to check first the current project version. (E.g. the current version is `2.1.0-SNAPSHOT`)
	```
	mvn flow:set-version -DnewVersion=2.1.0-XYZ-42-SNAPSHOT
	```
* Commit the version changes
	```
	git commit -a -m "XYZ-42: Updated version of feature branch"
	```
* Rebase the feature branch without version change commit on top of temporary feature branch. You have to find first the commit ID (SHA-1) of the version change commit (the first commit) on the feature branch. (E.g. commit ID is 1234567890abcdef1234567890abcdef12345678)
	```
	git rebase --onto tmp-feature/XYZ-42 1234567890abcdef1234567890abcdef12345678 feature/XYZ-42
	```
* Resolve rebase conflicts if any occur and continue with `git rebase --continue`
* Delete temporary feature branch
	```
	git branch -D tmp-feature/XYZ-42
	```
* Optionally build the project in order to test it.
* Push the rebased feature branch to the remote repository
	```
	git push -f origin feature/XYZ-42
	```

How do I automatically create Jenkins jobs for feature branches?
----------------------------------------------------------------

Add this to your pom.xml:

```
<properties>
    <jobBuild>true</jobBuild>
</properties>
```

**Note:** this will cause that Jenkins jobs will be automatically created also for new epic branches if used.
