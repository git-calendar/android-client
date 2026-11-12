GO ?= go
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ANDROID_NDK_HOME ?= $(lastword $(sort $(wildcard $(ANDROID_HOME)/ndk/*)))
ANDROID_API ?= 31
AAR := app/libs/core.aar
TOOLS_DIR := $(abspath build/go-tools)
CORE_DIR := ../git-calendar-core

.PHONY: core core-test clean-core

# Build the gomobile AAR from git-calendar-core's api package (pkg/api).
core: $(AAR)

$(AAR): $(shell find $(CORE_DIR)/pkg $(CORE_DIR)/go.mod -type f)
	mkdir -p app/libs $(TOOLS_DIR)
	cd $(CORE_DIR) && $(GO) build -o $(TOOLS_DIR)/gobind golang.org/x/mobile/cmd/gobind
	cd $(CORE_DIR) && PATH="$(TOOLS_DIR):$$PATH" ANDROID_HOME="$(ANDROID_HOME)" ANDROID_NDK_HOME="$(ANDROID_NDK_HOME)" \
		$(GO) tool gomobile bind -target=android/arm64,android/amd64 -androidapi $(ANDROID_API) \
		-javapkg dev.jotalac.core -ldflags="-s -w" -o $(abspath $(AAR)) ./pkg/api
	rm -f app/libs/core-sources.jar

core-test:
	cd $(CORE_DIR) && $(GO) vet ./pkg/api && $(GO) test ./pkg/api

clean-core:
	rm -f $(AAR)
