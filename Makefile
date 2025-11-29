.PHONY: all
all: install test build-image

TAG?=$(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

COSIGN_PASSWORD := $(COSIGN_PASSWORD)


REPACKAGE ?= true

.PHONY: clean
clean:
	./mvnw clean

.PHONY: build-tools
build-tools:
	./mvnw clean install -pl build-tools/

.PHONY: lint
lint: build-tools
	./mvnw validate -Dqa -fae -ntp -T1C

.PHONY: lint-pom
lint-pom:
	./mvnw validate -Dqa -fae -Dspotless.skip=true -Dcheckstyle.skip=true -ntp -T1C

.PHONY: lint-java
lint-java: build-tools
	./mvnw validate -Dqa -fae -Dsortpom.skip=true -ntp -T1C

.PHONY: format
format: format-pom format-java

.PHONY: format-pom
format-pom:
	./mvnw sortpom:sort -ntp -T1C

.PHONY: format-java
format-java:
	./mvnw spotless:apply -ntp -T1C

.PHONY: install
install: build-tools
	./mvnw clean install -DskipTests -ntp -U -T1C -pl src/starters/spring-boot3,src/starters/observability-spring-boot-3 -am
	./mvnw clean install -DskipTests -ntp -U -T1C

.PHONY: package
package:
	./mvnw clean package -Dfmt.skip -DskipTests -ntp -U -T1C

.PHONY: test
test:
	./mvnw verify -Dfmt.skip -ntp -T1C

.PHONY: build-image
build-image: build-base-images build-image-infrastructure build-image-geoserver

.PHONY: build-base-images
build-base-images: package-base-images
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0 TAG=$(TAG) \
	docker compose -f docker-build/base-images.yml build jre
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0 TAG=$(TAG) \
	docker compose -f docker-build/base-images.yml build spring-boot
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0 TAG=$(TAG) \
	docker compose -f docker-build/base-images.yml build spring-boot3
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0 TAG=$(TAG) \
	docker compose -f docker-build/base-images.yml build geoserver-common

.PHONY: build-image-infrastructure
build-image-infrastructure: package-infrastructure-images
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0 TAG=$(TAG) \
	docker compose -f docker-build/infrastructure.yml build

# This uses $(MAKECMDGOALS) (all targets specified) and filters out the target itself ($@), passing the rest as arguments. The %: rule tells make to ignore any unrecognized "targets" (which are actually your service names).
# Then you can call:
#  make build-image-geoserver wcs wfs
.PHONY: build-image-geoserver
build-image-geoserver: package-geoserver-images
	COMPOSE_DOCKER_CLI_BUILD=0 DOCKER_BUILDKIT=0  TAG=$(TAG) \
	docker compose -f docker-build/geoserver.yml build $(filter-out $@ build-image build-image-multiplatform,$(MAKECMDGOALS))

.PHONY: build-image-multiplatform
build-image-multiplatform: build-base-images-multiplatform build-image-infrastructure-multiplatform build-image-geoserver-multiplatform

.PHONY: build-base-images-multiplatform
build-base-images-multiplatform: package-base-images
	COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 TAG=$(TAG) \
	docker compose -f docker-build/base-images-multiplatform.yml build jre --push \
	&& COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 TAG=$(TAG) \
	   docker compose -f docker-build/base-images-multiplatform.yml build spring-boot --push \
	&& COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 TAG=$(TAG) \
	   docker compose -f docker-build/base-images-multiplatform.yml build spring-boot3 --push \
	&& COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 TAG=$(TAG) \
	   docker compose -f docker-build/base-images-multiplatform.yml build geoserver-common --push

.PHONY: build-image-infrastructure-multiplatform
build-image-infrastructure-multiplatform: package-infrastructure-images
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	docker compose -f docker-build/infrastructure-multiplatform.yml build --push

# This uses $(MAKECMDGOALS) (all targets specified) and filters out the target itself ($@), passing the rest as arguments. The %: rule tells make to ignore any unrecognized "targets" (which are actually your service names).
# Then you can call:
#  make build-image-geoserver-multiplatform wcs wfs
.PHONY: build-image-geoserver-multiplatform
build-image-geoserver-multiplatform: package-geoserver-images
	COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 TAG=$(TAG) \
	docker compose -f docker-build/geoserver-multiplatform.yml build --push $(filter-out $@ build-image build-image-multiplatform,$(MAKECMDGOALS))

.PHONY: package-base-images
package-base-images:
ifeq ($(REPACKAGE), true)
	./mvnw clean package -Dmaven.test.skip=true -T1C -ntp -am -pl src/apps/base-images/jre,src/apps/base-images/spring-boot,src/apps/base-images/spring-boot3,src/apps/base-images/geoserver
else
	@echo "Not re-packaging base images, assuming the target/*-bin.jar files exist"
endif

.PHONY: package-infrastructure-images
package-infrastructure-images:
ifeq ($(REPACKAGE), true)
	./mvnw clean package -Dmaven.test.skip=true -T1C -ntp -am -pl src/apps/infrastructure/config,src/apps/infrastructure/discovery,src/apps/infrastructure/gateway
else
	@echo "Not re-packaging infra images, assuming the target/*-bin.jar files exist"
endif

.PHONY: package-geoserver-images
package-geoserver-images:
ifeq ($(REPACKAGE), true)
	./mvnw clean package -Dmaven.test.skip=true -T1C -ntp -am -pl src/apps/geoserver/gwc,src/apps/geoserver/restconfig,src/apps/geoserver/wcs,src/apps/geoserver/webui,src/apps/geoserver/wfs,src/apps/geoserver/wms,src/apps/geoserver/wcs,src/apps/geoserver/wps
else
	@echo "Not re-packaging geoserver images, assuming the target/*-bin.jar files exist"
endif

.PHONY: pull-images
pull-images:
	TAG=$$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout) \
	docker compose \
     -f docker-build/geoserver-multiplatform.yml \
     -f docker-build/infrastructure-multiplatform.yml \
     pull --quiet

.PHONY: sign-image
sign-image:
	@bash -c '\
	TAG=$$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout); \
	images=$$(TAG=$$TAG docker compose -f docker-build/geoserver-multiplatform.yml -f docker-build/infrastructure-multiplatform.yml config --images); \
	for image in $$images; do \
	  echo "Signing $$image"; \
	  output=$$(cosign sign --yes --key env://COSIGN_KEY --recursive $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Signing successful: $$output"; \
	  fi; \
	done'

.PHONY: verify-image
verify-image:
	@bash -c '\
	TAG=$$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout); \
	images=$$(TAG=$$TAG docker compose -f docker-build/geoserver-multiplatform.yml -f docker-build/infrastructure-multiplatform.yml config --images); \
	for image in $$images; do \
	  echo "Verifying $$image"; \
	  output=$$(cosign verify --key env://COSIGN_PUB_KEY $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Verification successful: $$output"; \
	  fi; \
	done'

.PHONY: sign-image-keyless
sign-image-keyless:
	@bash -c '\
	TAG=$$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout); \
	images=$$(TAG=$$TAG docker compose -f docker-build/geoserver-multiplatform.yml -f docker-build/infrastructure-multiplatform.yml config --images); \
	for image in $$images; do \
	  echo "Signing $$image with keyless signing..."; \
	  cosign sign --yes --recursive $$image; \
	  if [ $$? -ne 0 ]; then \
	    echo "Error signing $$image"; \
	    exit 1; \
	  else \
	    echo "✓ Signed $$image"; \
	  fi; \
	done'

.PHONY: verify-image-keyless
verify-image-keyless:
	@echo "Note: Keyless verification requires certificate identity information from the signature."
	@echo "For images signed in GitHub Actions, use the workflow verification command."
	@bash -c '\
	TAG=$$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout); \
	images=$$(TAG=$$TAG docker compose -f docker-build/geoserver-multiplatform.yml -f docker-build/infrastructure-multiplatform.yml config --images); \
	for image in $$images; do \
	  echo "Verifying $$image..."; \
	  cosign verify $$image \
	    --certificate-identity-regexp=".*" \
	    --certificate-oidc-issuer-regexp=".*" || \
	  { echo "Failed to verify $$image"; exit 1; }; \
	  echo "✓ Verified $$image"; \
	done'

.PHONY: build-acceptance
build-acceptance:
	docker build --tag=geoservercloud/acceptance:latest acceptance_tests

.PHONY: acceptance-tests-datadir
acceptance-tests-datadir: build-acceptance start-acceptance-tests-datadir run-acceptance-tests-datadir

.PHONY: start-acceptance-tests-datadir
start-acceptance-tests-datadir:
	(cd compose/ && TAG=$(TAG) ./acceptance_datadir up -d)

.PHONY: run-acceptance-tests-datadir
run-acceptance-tests-datadir:
	(cd compose/ && ./acceptance_datadir run --rm -T acceptance bash -c 'until [ -f /tmp/healthcheck ]; do echo "Waiting for /tmp/healthcheck to be available..."; sleep 5; done && pytest . -vvv --color=yes')

.PHONY: clean-acceptance-tests-datadir
clean-acceptance-tests-datadir:
	(cd compose/ && TAG=$(TAG) ./acceptance_datadir down -v)

.PHONY: acceptance-tests-pgconfig
acceptance-tests-pgconfig: build-acceptance start-acceptance-tests-pgconfig run-acceptance-tests-pgconfig

.PHONY: start-acceptance-tests-pgconfig
start-acceptance-tests-pgconfig:
	(cd compose/ && TAG=$(TAG) ./acceptance_pgconfig up -d)

.PHONY: run-acceptance-tests-pgconfig
run-acceptance-tests-pgconfig:
	(cd compose/ && ./acceptance_pgconfig run --rm -T acceptance bash -c 'until [ -f /tmp/healthcheck ]; do echo "Waiting for /tmp/healthcheck to be available..."; sleep 5; done && pytest . -vvv --color=yes')

.PHONY: clean-acceptance-tests-pgconfig
clean-acceptance-tests-pgconfig:
	(cd compose/ && TAG=$(TAG) ./acceptance_pgconfig down -v)

.PHONY: acceptance-tests-jdbcconfig
acceptance-tests-jdbcconfig: build-acceptance start-acceptance-tests-jdbcconfig run-acceptance-tests-jdbcconfig

.PHONY: start-acceptance-tests-jdbcconfig
start-acceptance-tests-jdbcconfig:
	(cd compose/ && ./acceptance_jdbcconfig up -d)

.PHONY: run-acceptance-tests-jdbcconfig
run-acceptance-tests-jdbcconfig:
	(cd compose/ && ./acceptance_jdbcconfig run --rm -T acceptance bash -c 'until [ -f /tmp/healthcheck ]; do echo "Waiting for /tmp/healthcheck to be available..."; sleep 5; done && pytest . -vvv --color=yes')

.PHONY: clean-acceptance-tests-jdbcconfig
clean-acceptance-tests-jdbcconfig:
	(cd compose/ && ./acceptance_jdbcconfig down -v)

## Native Image Builds (GraalVM)
## Note: Requires GraalVM JDK 21+ to be installed

.PHONY: build-native-gateway
build-native-gateway:
	@echo "Building gateway service as GraalVM native image..."
	@echo "This will take 5-10 minutes and requires ~8GB RAM"
	(cd src/apps/infrastructure/gateway && ./mvnw clean package -Pnative -DskipTests)
	@echo "Native executable built at: src/apps/infrastructure/gateway/target/gs-cloud-gateway"

.PHONY: build-native-gateway-image
build-native-gateway-image:
	@echo "Building gateway native Docker image using Paketo buildpacks..."
	(cd src/apps/infrastructure/gateway && ./mvnw spring-boot:build-image -Pnative)

.PHONY: test-native-gateway
test-native-gateway:
	@echo "Testing gateway native executable..."
	@if [ -f src/apps/infrastructure/gateway/target/gs-cloud-gateway ]; then \
		echo "Starting native gateway..."; \
		src/apps/infrastructure/gateway/target/gs-cloud-gateway & \
		PID=$$!; \
		echo "Waiting for startup..."; \
		sleep 5; \
		curl -f http://localhost:8080/actuator/health && echo "\n✓ Gateway health check passed" || echo "\n✗ Gateway health check failed"; \
		kill $$PID; \
	else \
		echo "Error: Native executable not found. Run 'make build-native-gateway' first."; \
		exit 1; \
	fi

# Prevent make from treating service names as targets when using $(MAKECMDGOALS) in build-image-geoserver/build-image-geoserver-multiplatform
%:
	@:
