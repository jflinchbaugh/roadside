# mapmarks-server

Multi-tenant backend for map bookmarks from the website client.

run the server:
```
$ clj -M:run
```

register a new mapmarks account with login and password:
```
$ curl -s -v \
  -d login=u -d password=p \
  http://localhost:8080/mapmarks/api/register
```

Download the mapmarks mark data:
```
$ curl -s -v -u u:p http://localhost:8080/mapmarks/api/marks
```

Post new document data:
```
$ curl -s -v -u u:p \
  -H 'Content-Type: application/json' \
  -d '{"categories": [{"thing": true,"whatever": 2}]}' \
  http://localhost:8080/storage/api/logger/z
```

delete a mark:
```
$ curl -s -v -u u:p -X delete http://localhost:8080/mapmarks/api/marks/:id
```

static content, like the stylesheet, is available as well:
```
$ curl -s -v http://localhost:8080/css/style.css
```

run the tests:
```
$ clj -T:build test
```

run a single test:
```
$ clj -T:build test -n server.auth-test
```

build the container image:
```
$ make
```

start the containers: app + xtdb 2.1:
```
$ podman kube play mapmarks-server.yaml
```

stop the containers: app + xtdb 2.1:
```
$ podman kube down mapmarks-server.yaml
```

Create a system service around the pods:
```
$ sudo cp mapmarks-server.service /usr/lib/systemd/system/mapmarks-server.service
$ sudo systemctl daemon-reload
$ sudo systemctl enable mapmarks-server
$ sudo systemctl start mapmarks-server
$ sudo systemctl stop mapmarks-server
```

Create a user Quadlet to run pods:
```
$ loginctl enable-linger # so our services will start at boot and stay around
$ cp mapmarks-server.kube mapmarks-server.yaml $HOME/.config/containers/systemd/
$ systemctl --user daemon-reload
$ systemctl --user start mapmarks-server
```

Access the running XTDB server from the api server:
```
$ podman exec -it mapmarks-server-api psql -U xtdb -h xtdb
```

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
