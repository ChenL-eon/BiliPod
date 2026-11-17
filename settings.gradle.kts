pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 本机有全局 init 脚本（~/.gradle/init.d/cn-mirrors.gradle）会往所有工程的 buildscript
    // 和 project 仓库里插镜像。原先用 FAIL_ON_PROJECT_REPOS 会直接报冲突，
    // 这里改成 PREFER_SETTINGS：以本文件的仓库为准，init 脚本注入的项目级仓库被忽略，
    // 同时它注入到 settings 级的镜像依旧生效（国内拉依赖更快）。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BiliPod"
include(":app")
