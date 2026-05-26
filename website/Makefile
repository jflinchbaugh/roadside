.PHONY: all install dev dev-test test test-browser release clean

all: release

install:
	npm install

dev: install
	npx shadow-cljs watch frontend

dev-test: install
	npx shadow-cljs watch frontend test test-browser

test: install
	npx shadow-cljs compile test

test-browser: install
	npx shadow-cljs watch test-browser

release: install
	npx shadow-cljs release frontend

clean:
	rm -rf out .shadow-cljs public/js public/index.html public/manifest.json src/main/com/hjsoft/mapmarks/website/version.cljs
