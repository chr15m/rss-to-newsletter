build: src/**/*.cljs shadow-cljs.edn package.json public/**/**
	rm -rf build
	rsync -aLz --exclude js public/ build
	npx shadow-cljs release app --debug

server.js: src/**/*.cljs shadow-cljs.edn
	npx shadow-cljs release server --debug

test.js: src/**/*.cljs shadow-cljs.edn
	npx shadow-cljs release tests

test: test.js
	node test.js

.PHONY: watch watcher server repl

server:
	rm -f devserver.js
	until [ -f devserver.js ]; do sleep 1; done
	sleep 1 && node devserver.js

watcher:
	npx shadow-cljs watch server app

watch:
	make -j2 watcher server

repl:
	npx shadow-cljs cljs-repl app
