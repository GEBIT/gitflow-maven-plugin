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

Typical steps for implementing a feature in a feature branches are outlined in [Working on Features](working-on-features.html).

A complete list of goals with all properties is available from the [Goals List](plugin-info.html).

### Issues and Suggestions

If you have issues or improvement suggestions, please use our [JIRA](https://jira.gebit.de/secure/CreateIssue!Default.jspa?pid=10511)
