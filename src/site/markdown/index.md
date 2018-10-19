Git-Flow Maven Plugin
---------------------

The Maven plugin for Vincent Driessen's [successful Git branching model](http://nvie.com/posts/a-successful-git-branching-model/).

We use a modified branching model with some specialities:

* Support for maintenance branches
* Support for epic branches
* Automatic version changes for feature branches

You perform commands by invoking goals like this:

```
mvn flow:[goal]
```

### Prerequisites

You will need to have some software installed:

* An installation of Java. Your `JAVA_HOME` environment should be set accordingly. It is not required that `java` is on
  the path, but it is recommended.
* An installation of Maven, currently preferably [Maven 3.5.3](https://archive.apache.org/dist/maven/maven-3/3.5.3/binaries/).
  Extract this installation and make sure the `mvn` executable/script is on the path
* Access to the git repository must be configured in a way that does not prompt for passwords. To setup a private/public
  key authenticated git connection instead of a password based have a look at [SSH Key erzeugen](https://wiki.gebit.de/display/GIT/SSH+Key+erzeugen).


### Usage

Typical steps for implementing a feature in a feature branches

+ Start a new feature branch:

```
mvn flow:feature-start
```

Make sure your local master is not behind the remote, before executing.

+ Add a Jenkins build job for the feature branch:

If you're on the feature branch:
```
mvn flow:branch-config -DpropertyName=JOB_BUILD -DpropertyValue=true
```
otherwise:
```
mvn flow:branch-config -DfeatureName=XYZ-1234 -DpropertyName=JOB_BUILD -DpropertyValue=true
```

(you will need to use the correct branch name, of course).

You can also do this right away when starting the feature branch:

```
mvn flow:feature-start -DjobBuild=true
```


+ Rebasing a feature on the current upstream branch

```
mvn flow:feature-rebase -N
```

The `-N` option is needed for rare cases where a module in an upstream project is removed, that is still used in the 
current project before the rebase. If you don't specify `-N` yo will get errors about missing dependencies.


+ Finish a feature by merging (rebasing upon) into the upstream branch:

```
mvn flow:feature-finish
```

Make sure your local master branch is not behind the remote, before executing.


A complete list of goals with all properties is available from the [Goals List](plugin-info.html).

### Issues and Suggestions

If you have issues or improvement suggestions, please use our [JIRA](https://jira.gebit.de/secure/CreateIssue!Default.jspa?pid=10511)