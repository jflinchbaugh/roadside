SRC_FILES := $(shell find src/main deps.edn build.clj ../common/src -type f)
TEST_FILES := $(shell find src/test -type f)

all: container

.PHONY: clean run-server run-xtdb test uber container

test: target/.test-passed
uber: target/.uberjar-built

target/.test-passed: $(SRC_FILES) $(TEST_FILES)
	clj -T:build run-test && mkdir -p target && touch target/.test-passed

target/.uberjar-built: target/.test-passed $(SRC_FILES)
	clj -T:build uber
	touch target/.uberjar-built

container: target/.uberjar-built Containerfile
	podman build -t roadside-server:latest .

clean:
	clj -T:build clean

run-server:
	podman kube play --wait roadside-server.yaml

run-xtdb:
	podman kube play --wait roadside-xtdb.yaml
