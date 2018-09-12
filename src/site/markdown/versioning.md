Version Numbering Guidelines
----------------------------

In general we employ a standard Maven versioning scheme `Major.Minor.Revision[.Hotfix][-Qualifier]`, where:

* Increasing the major number marks a major extension of features or major change, that is expected to require additional migration work for adopter (e.g. a new UI Layer, change for OSGi to CDI, ...).
* Increasing the minor number marks a normally planned feature release with small required work for adopters.
* The revision number starts with `0` for every release and is incremented for each bugfix / maintenance release.
* The first release is `1.0.0`.
* An unplanned hotfix will receive an additional versioning component
* The build number is not part of the version.

The qualifier part marks a build as one of:

* `-SNAPSHOT` is a temporary snapshot build off the tip of a branch triggered by any change. It should not be referenced by downstream projects, as it can introduce breaking changes at any time. Snapshot builds are not archived and regularly purged.
* `-Ixxxx` is an integration build, which is also regularly built, but in a much lower frequency (like weekly) or on demand. An integration build can be referenced by downstream projects and will be kept available for some time (guaranteed 6 weeks, see the linked issues of https://jira.gebit.de/browse/GBLD-14 for details). Downstream projects should regularly update to the latest `I-Build` to track development in the upstream project. The upside to a `-SNAPSHOT` build is that the dependency is stable for some time and the project chooses at which time the upstream dependency is updated.
{note}
Integration builds are mostly suited for library/framework projects (which someone uses). In customer projects you could use it, too (e.g. for producing a site or a test build, as integration builds also create tags), but *NEVER* as a production release.
{note}
* No qualifier is a regular release build. Those builds are usually shipped and will be permanently archived.

### Special kinds of builds to consider:

#### Customer releases

If special adaptions (like hand-picked fixes) for a customer release are necessary, a customer release is created. This will be marked with a customer specific qualifier, like `-customer1`. This qualifier can be mixed with the other qualifiers, but will be placed first (e.g. 1.2.3-customer1-SNAPSHOT)

For a more elaborate description see [here|POS Customer Branch Lockdown]


#### Release Candidates

The normal flow for release candidates is using it as qualifiers, e.g. `1.2.3-RC1`. This can also be combined with other qualifiers (e.g. `1.2.3-RC1-SNAPSHOT`). In this way, `1.2.3-RC1` is a release and will normally be permanently archived.

An alternate flow is where you ship a release, but are not sure if it will be the final one (e.g. because you are waiting for QA approval). In this case, each candidate is handled like a release, but with a hotfix component in any case. The first release candidate has a `.0` hotfix version, e.g. `1.2.3.0`. The version is immediatly increased to the next hotfix version (e.g. `1.2.3.1-SNAPSHOT`) and the net release candidate thus becomes `1.2.3.1`.

Release Branches / Maintenance Branches
---------------------------------------

A release branch is a very short-lived branch just for the duration of a release (for atomicity). While you could use this to make a two-step release (`mvn flow:release-start` and `mvn flow:release-finish`) we prefer to release on a previously created maintenance branch instead. This will isolate the release for some time before from unwanted changes (lockdown). This will also make clear, that any changes happing to the development branch are already for the next release, as the Version is already incremented.

The downside of course is, that you will then need to cherry-pick any wanted change to the release branch.

So the branch to base a release on is always a _Maintenance Branch_.
