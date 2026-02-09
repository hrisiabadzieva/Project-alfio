[alf.io](https://alf.io)
========

The open source ticket reservation system.

[Alf.io](https://alf.io) <small>([[ˈalfjo]](https://en.m.wikipedia.org/wiki/Alfio))</small> is a free and open source event attendance management system, developed for event organizers who care about privacy, security and fair pricing policy for their customers.

[![Build Status](https://github.com/alfio-event/alf.io/workflows/build/badge.svg)](https://github.com/alfio-event/alf.io/actions)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=alfio-event_alf.io&metric=security_rating)](https://sonarcloud.io/summary/overall?id=alfio-event_alf.io)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=alfio-event_alf.io&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=alfio-event_alf.io)
[![codecov](https://codecov.io/gh/alfio-event/alf.io/graph/badge.svg?token=scWGkoUzoX)](https://codecov.io/gh/alfio-event/alf.io)
[![Financial Contributors on Open Collective](https://opencollective.com/alfio/all/badge.svg?label=financial+contributors)](https://opencollective.com/alfio)
[![Docker Hub Pulls](https://img.shields.io/docker/pulls/alfio/alf.io.svg)](https://hub.docker.com/r/alfio/alf.io/tags)
[![Open Source Helpers](https://www.codetriage.com/alfio-event/alf.io/badges/users.svg)](https://www.codetriage.com/alfio-event/alf.io)

<!-- TABLE OF CONTENTS -->
<details open>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#prerequisites">Prerequisites</a></li>
    <li><a href="#spring-profiles">Spring profiles</a></li>
    <li><a href="#run-in-development-mode">Run in development mode</a></li>
    <li><a href="#contributing-to-alf.io">Contributing to alf.io</a></li>
    <li><a href="#check-dependencies-to-update">Check dependencies to update</a></li>
    <li><a href="#running-docker-containers">Running Docker containers</a></li>
    <li><a href="#contributors">Contributors</a></li>

  </ol>
</details>

## Prerequisites

You should have installed Java version **17** (e.g. [Oracle's](http://www.oracle.com/technetwork/java/javase/downloads/index.html), [OpenJDK](http://openjdk.java.net/install/), or any other distribution) to build and run alf.io. Please note that for the build process the JDK is required.

Postgresql version 10 or later.

Additionally, the database user that creates and uses the tables should not be a "SUPERUSER", or else the row security policy checks will not be applied.

> [!NOTE]
> As the work for Alf.io [v2](https://github.com/alfio-event/alf.io/milestones) has started, this branch may contain **unstable** and **untested** code.
> If you want to build and deploy alf.io by yourself, please start from a [Released version](https://github.com/alfio-event/alf.io/releases).

## Spring profiles

There are the following spring profiles

- `dev`: enable dev mode
- `spring-boot`: added when launched by spring-boot
- `demo`: enable demo mode, the accounts for the admin will be created on the fly
- `disable-jobs`: disable jobs
- `jdbc-session`: persist the user session in the database

## Run in development mode

### Gradle Build

This build includes a copy of the Gradle wrapper. You don't have to have Gradle installed on your system to build
the project. Simply execute the wrapper along with the appropriate task, for example

```
./gradlew clean
```

#### Running with multiple profiles

You must specify a project property at the command line, such as
```
./gradlew -Pprofile=dev :bootRun
```
The local "bootRun" task has the following prerequisites:

- a PostgreSQL (version 10 or later) instance up and running on localhost:5432
- a _postgres_ user having a password: _password_
- a database named _alfio_

```
docker run -d --name alfio-db -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=alfio --restart unless-stopped postgres 
```

once started, alf.io will create all the required tables in the database, and be available at http://localhost:8080/admin. You can log in using the default Username _admin_ and the password which was printed on the console.

You can get a list of all supported Gradle tasks by running
```
./gradlew tasks --all
```

You can configure additional System properties (if you need them) by creating the following file and putting into it one property per line:
```
vi custom.jvmargs
```

Please be aware that since this file could contain sensitive information (such as Google Maps private API key) it will be automatically ignored by git.

#### For debug

Add a new line with: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` in custom.jvmargs


## Contributing to alf.io
Importing the Gradle project into Intellij and Eclipse both work.

**Notes**:
- this project uses [Project Lombok](https://projectlombok.org/). You will need to install the corresponding Lombok plugin for integration into your IDE.
- this project uses [TestContainers](https://testcontainers.org) to run integration tests against a real PostgreSQL database. Please make sure that your configuration meets [their requirements](https://www.testcontainers.org/supported_docker_environment/)

<details><summary>How to configure TestContainers with Podman (Fedora 34)</summary>
<p>
As TestContainers expect the docker socket for managing the containers, you will need to do the following <a href="https://github.com/containers/podman/issues/7927#issuecomment-732676422" target="_blank" rel="noopener">(see original issue for details)</a>:

Define the 2 env. variable:

```
export TESTCONTAINERS_RYUK_DISABLED=true
export DOCKER_HOST=unix:///run/user/1000/podman/podman.sock
```

And run in another console:

```
podman system service -t 0
```
To be noted:

- for unknown reason, the first time podman download the missing images, testcontainers will fail. Run another time and it will work.
- in theory, with systemd+socket activation the service should start automatically, but currently I was not able to make it works.

</p>
</details>


## Check dependencies to update

`./gradlew dependencyUpdates`

## Running Docker containers

Container images are available on https://hub.docker.com/r/alfio/alf.io/tags.

alf.io can also be run with Docker Compose (*development mode*):

    docker-compose up

Running alf.io in production using Docker compose is not officially supported.
However, if you decide to do so, then you need to make a couple of changes:

* Uncomment the `alfio` service in the `docker-compose.yml` file
* Check the user and password for the services in the `.env` file
* Handle SSL termination (e.g. with something like `tutum/haproxy`) 443 -> 8080

### Test alf.io application
* Check alfio logs: `docker logs alfio`
* Copy admin password in a secure place
* Get IP of your docker container: (only on Mac/Windows, on Linux the proxy will bind directly on your public IP)
    * `boot2docker IP` on Mac/Windows
* Open browser at: `https://DOCKER_IP/admin`
* Insert user admin and the password you just copied

### Generate a new version of the alfio/alf.io docker image

#### Build application and Dockerfile
 ```
 ./gradlew distribution
 ```
Alternatively, you can use Docker (*experimental*):
 ```
 docker run --rm -u gradle -v "$PWD":/home/gradle/project -w /home/gradle/project gradle:7.0.0-jdk11 gradle --no-daemon distribution -x test
 ```

Please note that at the moment the command above performs the build without running the automated tests.
Use it at your own risk.

#### Create docker image:
 ```
 docker build -t alfio/alf.io ./build/dockerize
 ```

### About the included AppleWWDRCAG4.cer

The certificate at src/main/resources/alfio/certificates/AppleWWDRCAG4.cer has been imported for https://github.com/ryantenney/passkit4j#usage functionality.
It will expire the 2030-10-12 (YYYY-MM-DD - as of https://www.apple.com/certificateauthority/).



## Contributors

### Code Contributors

This project exists thanks to all the people who contribute.
<a href="https://github.com/alfio-event/alf.io/graphs/contributors"><img src="https://opencollective.com/alfio/contributors.svg?width=890&button=false" /></a>

### Translation Contributors (POEditor)

A big "Thank you" goes also to our translators, who help us on [POEditor](https://github.com/alfio-event/alf.io/tree/master/src/main/resources/alfio/i18n):

(we show the complete name/profile only if we have received explicit consent to do so)

| Language        | Name          | Github  | Twitter |
| -------------   |:--------------| -----:  |---|
| Dutch (nl)      | Matthjis      |    | |
| Turkish (tr)    | Dilek         |      | |
| Spanish (es)    | Mario Varona  |    [@mvarona](https://www.github.com/mvarona)   | [@MarioVarona](https://www.twitter.com/MarioVarona) |
| Spanish (es)    | Sergi Almar   |    [@salmar](https://www.github.com/salmar)   | [@sergialmar](https://www.twitter.com/sergialmar) |
| Spanish (es)    | Jeremias      |    | |
| Bulgarian (bg)  | Martin Zhekov |  [@Martin03](https://www.github.com/Martin03)  | [@MartensZone](https://www.twitter.com/MartensZone) |
| Portuguese (pt) | Hugo          |    | |
| Swedish (sv)    | Johan         |    | |
| Romanian (ro)   | Daniel        |    | |
| Polish (pl)     | Pawel        |    | |
| Danish (da)     | Sune        |    | |

translations completed but not yet integrated (WIP)

| Language        | Name          | Github  | Twitter |
| -------------   |:--------------| -----:  |---|
| Japanese (jp) | Martin      |    | |
| Chinese (Taiwan) (cn_TW) | Yu-cheng, Lin      |    | |

### Sponsors

This project is sponsored by:

<a href="https://swicket.io" target="_blank">
  <img alt="Swicket" src="https://swicket.io/logo-web.png" width="200">
</a><br/>
<a href="https://www.browserstack.com/open-source" target="_blank"><img alt="Powered by BrowserStack" src="https://user-images.githubusercontent.com/2320747/150974875-769c7085-f8bf-49b8-aff9-b650231300eb.jpg" width="200"/> Open Source program</a>
<br><br>
<img alt="Exteso" src="https://alf.io/img/logos/exteso_logo.jpg" width="150"> &nbsp;
<a href="https://www.starplane.it/" target="_blank">
  <img alt="Starplane" src="https://alf.io/img/logos/starplane.png" width="120">
</a>&nbsp;
<a href="https://www.amicidelticino.ch/" target="_blank">
  <img alt="Amici del Ticino" src="https://alf.io/img/logos/amicidelticino-logo.png" width="120">
</a>&nbsp;
<a href="https://www.netlify.com/" target="_blank">
  <img alt="Netlify" src="https://www.netlify.com/img/global/badges/netlify-color-accent.svg" width="120">
</a>

### Financial Contributors

Become a financial contributor and help us sustain our community. [[Contribute](https://opencollective.com/alfio/contribute)]

#### Individuals

<a href="https://opencollective.com/alfio"><img src="https://opencollective.com/alfio/individuals.svg?width=890"></a>

#### Organizations

Support this project with your organization. Your logo will show up here with a link to your website. [[Contribute](https://opencollective.com/alfio/contribute)]

<a href="https://opencollective.com/alfio/organization/0/website"><img src="https://images.opencollective.com/spring_io/01a0fe1/logo.png" height="100"></a> &nbsp;
<a href="https://opencollective.com/alfio/organization/1/website"><img src="https://images.opencollective.com/salesforce/ca8f997/logo/256.png" height="100"></a> &nbsp;
<a href="https://opencollective.com/alfio/organization/2/website"><img src="https://opencollective.com/alfio/organization/2/avatar.svg"></a>

## Refactoring and Architectural Improvements

As part of this course project, a series of refactorings and architectural improvements were implemented with 
the main goal of improving code quality, maintainability, and adherence to SOLID principles, while ensuring that 
the project continues to build successfully.

### Dynamic Discount Message Refactor (SRP)

The first step focused on applying the Single Responsibility Principle (SRP) in the REST layer. Previously, the 
logic responsible for formatting dynamic discount messages was implemented directly inside `EventApiV2Controller`, 
using a switch-based structure. This caused the controller to mix request handling with business and formatting logic.

To address this, the formatting logic was extracted into a dedicated service named `DynamicDiscountMessageService`. 
The controller now delegates this responsibility to the service, resulting in a thinner controller with a clearer role. 
This refactor significantly improves readability, testability, and long-term maintainability.

The project was successfully compiled using Gradle (`testClasses`), confirming that this refactor did not break the build.

---

### StripeWebhookPaymentManager Refactor (SRP, OCP, DIP)

In the initial implementation of `StripeWebhookPaymentManager`, the handling of different Stripe webhook 
events was implemented through a large `switch` statement based on the event type. This approach led to several design issues:

- Violation of the Single Responsibility Principle (SRP), as the class both managed webhook orchestration and contained 
business logic for each event type.
- Violation of the Open/Closed Principle (OCP), since adding a new webhook type required modifying the existing switch statement.
- Strong coupling between the control flow and concrete event types.

#### Implemented Solution

The logic from the switch construct was extracted into separate handlers, each responsible for processing a single 
webhook event type (e.g. `created`, `succeeded`, `failed`). A dispatch mechanism was introduced using a `Map<String, Handler>`, 
allowing the appropriate handler to be selected dynamically based on the webhook event type.

The main manager class now has a clearly defined role:
- validating input data,
- extracting the required context,
- delegating processing to the appropriate handler.

As a result, new webhook types can be added by introducing new handlers, without modifying existing logic.

The same approach was consistently applied to both:
- `StripeWebhookPaymentManager`
- `MollieWebhookPaymentManager`

---

### Liskov Substitution Principle (LSP) Fix

An additional design issue was identified in `PurchaseContextSearchManager`. Some methods accepted the base 
type `PurchaseContext` but threw `UnsupportedOperationException` for specific subtypes (e.g. subscriptions). 
This violated the Liskov Substitution Principle, as subtypes could not safely replace the base type.

The solution was to replace runtime exceptions with valid, neutral return values such as empty collections or default pairs. 
This ensures that all subtypes are handled consistently and eliminates unexpected runtime crashes.

---

### NotificationManager Refactor (Major Cleanup)

At the start of the project, `NotificationManager` was a large, monolithic class with excessive responsibilities. 
It handled email sending, attachment generation (ICS, PDFs), template selection, and direct knowledge of Stripe, PassKit, 
and other subsystems. The constructor contained over 20 dependencies, static helpers were used internally, and the 
class was extremely difficult to test or modify safely.

#### Refactoring Steps

Attachment generation logic was extracted into a dedicated `AttachmentGenerator` abstraction, with concrete implementations 
such as `IcsAttachmentGenerator`. The generators were injected via dependency injection as a collection and organized into a 
map keyed by attachment type. This removed the need for large conditional blocks and static helpers.

The constructor of `NotificationManager` was simplified to include only its true orchestration dependencies. 
All low-level responsibilities were moved into their respective classes. As a result, `NotificationManager` now acts 
purely as an orchestration component, coordinating email sending and attachment selection without generating content itself.

---

### Stripe SDK Decoupling (DIP)

Another major issue was the tight coupling between Stripe-related classes and the Stripe SDK. Webhook parsing and 
verification were performed directly inside core classes, making tests fragile and highly dependent on external libraries.

To address this, a new abstraction was introduced:

```java
interface StripeWebhookEventParser {
    StripeWebhookEvent parseAndVerify(...);
}

