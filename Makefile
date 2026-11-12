CORE_VERSION := v0.4.0
AAR := app/libs/core.aar

.PHONY: fetch-core fetch-core-latest

# Download the core AAR from git-calendar/core releases.
fetch-core:
	mkdir -p app/libs
	curl -fL -o $(AAR) https://github.com/git-calendar/core/releases/download/$(CORE_VERSION)/core.aar

fetch-core-latest:
	mkdir -p app/libs
	curl -fL -o $(AAR) https://github.com/git-calendar/core/releases/latest/download/core.aar
