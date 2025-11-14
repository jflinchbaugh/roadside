all: container

.PHONY: uber

uber:
	clj -T:build uber

.PHONY: container

container: uber
	podman build -t roadside-server:latest .

run-server:
	podman kube play --wait roadside-server.yaml

run-xtdb:
	podman kube play --wait roadside-xtdb.yaml
