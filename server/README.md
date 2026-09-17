# mapmarks-server

Multi-tenant backend for map bookmarks from the website client.

## Multi-Tenancy and Site Routing

The server supports multi-tenancy by scoping tenant datasets under a `:site`
identifier (e.g. `mapmarks`, `roadside`, `potholes`, `library`).

All site-specific endpoints are nested under `/s/:site/`
(e.g., `<base-url>/s/:site/api/...` and `<base-url>/s/:site/:filename`).
The `/s/` path segment acts as a dedicated namespace prefix, ensuring that
dynamic site identifiers never collide with other top-level API routes (such as
`/api/ping`) or static asset paths (such as `/css/style.css` or `/js/main.js`).

run the server:
```
$ clj -M:run
```

register a new account for a site with login and password:
```
$ curl -s -v \
  -d login=u -d password=p -d email=u@example.com \
  http://localhost:8080/mapmarks/s/mapmarks/api/register
```

download the marks data for a site:
```
$ curl -s -v -u u:p http://localhost:8080/mapmarks/s/mapmarks/api/marks
```

create a new mark for a site:
```
$ curl -s -v -u u:p \
  -H 'Content-Type: application/json' \
  -d '{"name": "Sample Mark", "lat": 40.0, "lon": -76.0}' \
  http://localhost:8080/mapmarks/s/mapmarks/api/marks
```

delete a mark:
```
$ curl -s -v -u u:p -X delete \
  http://localhost:8080/mapmarks/s/mapmarks/api/marks/:id
```

download site feeds (RSS, KML, CSV):
```
$ curl -s -v http://localhost:8080/mapmarks/s/mapmarks/feed.rss
```

static content, like the stylesheet, is available at top-level paths:
```
$ curl -s -v http://localhost:8080/mapmarks/css/style.css
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
