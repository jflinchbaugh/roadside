SRC_FILES := $(shell find src/main deps.edn build.clj ../common/src -type f)
TEST_FILES := $(shell find src/test -type f)

all: container

.PHONY: clean run-server run-xtdb test uber container

test: $(SRC_FILES) $(TEST_FILES)
	clj -T:build test

uber: target/.uberjar-built
target/.uberjar-built: $(SRC_FILES)
	clj -T:build uber
	touch target/.uberjar-built

container: test target/.uberjar-built Containerfile
	podman build -t roadside-server:latest .

clean:
	clj -T:build clean

run-server:
	podman kube play --wait roadside-server.yaml

run-xtdb:
	podman kube play --wait roadside-xtdb.yaml
