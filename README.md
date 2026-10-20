# Samanvay BPM Platform - The open source BPMN platform

Samanvay BPM Platform is a flexible framework for workflow and process automation. Its core is a native BPMN 2.0 process engine that runs inside the Java Virtual Machine. It can be embedded inside any Java application and any Runtime Container. It integrates with Jakarta/Java EE and is a perfect match for the Spring Framework. On top of the process engine, you can choose from a stack of tools for human workflow management, operations and monitoring.

> [!Note]
> Samanvay BPM Platform is a fork of [Camunda Platform 7 Community Edition](https://github.com/camunda/camunda-bpm-platform) (7.24). It keeps API, Maven coordinates (`org.camunda.bpm`), Java packages (`org.camunda.*`) and the `camunda:` BPMN/DMN extension namespace compatible with upstream, so existing process models, plugins and the Camunda Modeler keep working. "Camunda" is a trademark of Camunda Services GmbH; this project is not affiliated with or endorsed by Camunda.

## Components

Samanvay BPM Platform provides a rich set of components centered around the BPM lifecycle.

#### Process Implementation and Execution

- Process Engine - The core component responsible for executing BPMN 2.0 processes.
- REST API - The REST API provides remote access to running processes.
- Spring, CDI Integration - Programming model integration that allows developers to write Java Applications that interact with running processes.

#### Process Design

- Any BPMN 2.0 modeler that supports the `camunda:` extension namespace, e.g. the [Camunda Modeler](https://github.com/camunda/camunda-modeler) desktop application.

#### Process Operations

- Process Engine - JMX and advanced Runtime Container Integration for process engine monitoring.
- Samanvay Cockpit - Web application tool for process operations.
- Samanvay Admin - Web application for managing users, groups, and their access permissions.

#### Human Task Management

- Samanvay Tasklist - Web application for managing and completing user tasks in the context of processes.

#### And there's more...

- [bpmn.io](https://bpmn.io/) - Toolkits for BPMN, CMMN, and DMN in JavaScript (rendering, modeling)
- The [Camunda 7 manual](https://docs.camunda.org/manual/7.24/) largely applies to this platform as well.

## A Framework

Samanvay BPM Platform strives to be highly integrable and embeddable. We seek to deliver a great experience to developers that want to use BPM technology in their projects.

### Highly Integrable

Out of the box, Samanvay BPM Platform provides infrastructure-level integration with Jakarta/Java EE Application Servers and Servlet Containers.

### Embeddable

Most of the components that make up the platform can even be completely embedded inside an application. For instance, you can add the process engine and the REST API as a library to your application and assemble your custom BPM platform configuration.

## Building

```sh
./mvnw clean install -DskipTests
```

## Contributing

Please see our [contribution guidelines](CONTRIBUTING.md) for how to raise issues and how to contribute code to our project.

## Tests

To run the tests in this repository, please see our [testing tips and tricks](TESTING.md).

## License

The source files in this repository are made available under the [Apache License Version 2.0](./LICENSE). See [NOTICE](./NOTICE) for attribution of the original Camunda Platform 7 code.

Samanvay BPM Platform uses and includes third-party dependencies published under various licenses. Refer to https://docs.camunda.org/manual/latest/introduction/third-party-libraries/ for an overview of third-party libraries and particularly important third-party licenses.
