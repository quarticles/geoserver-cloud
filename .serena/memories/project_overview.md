# GeoServer Cloud - Project Overview

## Purpose
GeoServer Cloud is a cloud-native, microservices-based distribution of GeoServer built on Spring Boot and Spring Cloud. It decomposes the traditional GeoServer monolith into independently deployable, scalable microservices for each OWS service, the Web UI, and the REST API.

## Tech Stack
- **Language**: Java 21
- **Build**: Maven with Makefile wrapper
- **Framework**: Spring Boot 3, Spring Cloud
- **Service Discovery**: Spring Cloud Netflix Eureka
- **Configuration**: Spring Cloud Config
- **Gateway**: Spring Cloud Gateway
- **Messaging**: Spring Cloud Bus with RabbitMQ
- **Containerization**: Docker with multi-platform support (amd64/arm64)
- **Registry**: GitHub Container Registry (ghcr.io/quarticles)

## Microservices Architecture

### Infrastructure Services
- `config` - Spring Cloud Config server (externalized configuration)
- `discovery` - Spring Cloud Netflix Eureka (service discovery)
- `gateway` - Spring Cloud Gateway (API gateway, single entry point)

### GeoServer Services
- `wms` - Web Map Service
- `wfs` - Web Feature Service
- `wcs` - Web Coverage Service
- `wps` - Web Processing Service
- `gwc` - GeoWebCache (tile caching)
- `restconfig` - REST Configuration API
- `webui` - Administration Web UI

## Catalog Backends
1. **pgconfig** (Recommended for production) - PostgreSQL-based catalog backend
2. **datadir** - Traditional shared data directory (requires ReadWriteMany volume in k8s)
3. **jdbcconfig** (Deprecated) - JDBC-based catalog backend

## Key Dependencies
- Custom GeoServer branch at https://github.com/camptocamp/geoserver-cloud-geoserver
- Quarticle fork of imageio-ext for S3-compatible endpoints: io.github.quarticles:imageio-ext-cog-rangereader-s3
- Config submodule at https://github.com/geoserver/geoserver-cloud-config

## Access Points
- Development: http://localhost:9090/geoserver/cloud/
- Default credentials: admin/geoserver
