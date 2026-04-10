# roadside-server

run the server:
```
$ clj -M:run
```

register a new roadside account with login and password:
```
$ curl -s -v \
  -d login=u -d password=p \
  http://localhost:8080/roadside/api/register
```

Download the roadside stand data:
```
$ curl -s -v -u u:p http://localhost:8080/roadside/api/stands
```

Post new document data:
```
$ curl -s -v -u u:p \
  -H 'Content-Type: application/json' \
  -d '{"categories": [{"thing": true,"whatever": 2}]}' \
  http://localhost:8080/storage/api/logger/z
```

delete a stand:
```
$ curl -s -v -u u:p -X delete http://localhost:8080/roadside/api/stands/:id
```

static content, like the stylesheet, is available as well:
```
$ curl -s -v http://localhost:8080/css/style.css
```

run the tests:
```
$ make test
```

run a single test:
```
$ clj -T:build run-test -n server.auth-test
```

build the container image:
```
$ make
```

start the containers: app + xtdb 2.1:
```
$ podman kube play roadside-server.yaml
```

stop the containers: app + xtdb 2.1:
```
$ podman kube down roadside-server.yaml
```

Create a system service around the pods:
```
$ sudo cp roadside-server.service /usr/lib/systemd/system/roadside-server.service
$ sudo systemctl daemon-reload
$ sudo systemctl enable roadside-server
$ sudo systemctl start roadside-server
$ sudo systemctl stop roadside-server
```

Create a user Quadlet to run pods:
```
$ loginctl enable-linger # so our services will start at boot and stay around
$ cp roadside-server.kube roadside-server.yaml $HOME/.config/containers/systemd/
$ systemctl --user daemon-reload
$ systemctl --user start roadside-server
```

Access the running XTDB server from the api server:
```
$ podman exec -it roadside-server-api psql -U xtdb -h xtdb
```

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
