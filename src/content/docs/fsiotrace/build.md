---
title: 빌드 가이드 (NDK + Bionic)
description: Android NDK + Bionic 로 fsiotrace 를 cross-compile 하는 절차 (Docker / Ubuntu)
---

타겟: Android device 의 native 환경에서 동작해야 하므로 **Android NDK + Bionic**
로 빌드한다. glibc 정적 ELF 는 device 환경에서 막히는 경우가 있어 NDK 경로
를 표준으로 채택.

핵심 도전: libelf 가 NDK sysroot 에 없음 → `scripts/build_libelf_ndk.sh` 가
elfutils-0.191 을 받아 Bionic 호환 패치(libintl stub, obstack 추가,
configure cache) 를 자동 적용해 한 번에 빌드.

## 가장 빠른 경로: `scripts/build.sh` 한 줄

```sh
bash scripts/build.sh
```

이 스크립트가 자동으로:
- NDK 경로 검색 / `ANDROID_NDK_HOME` 확인
- 호스트 OS 감지해 NDK_HOST_OS 선택
- vmlinux.btf 없으면 `adb pull` 시도
- libelf 없으면 `build_libelf_ndk.sh` 호출
- `make` + `adb push`

옵션:
```sh
--no-push       # 빌드만
--clean         # out/ + third_party/elfutils/ + libelf-android/ 청소 후
--ndk PATH      # NDK 경로 명시
--btf PATH      # 이미 받아둔 BTF 사용
--serial S      # 특정 adb device
```

## 나머지 옵션 (수동 제어)

두 경로:
1. **Docker** — 어떤 호스트든 동일 환경
2. **Ubuntu 22.04+ 직접**

## 사전: Android NDK 설치

```sh
# Ubuntu sdkmanager
sudo apt install -y default-jre wget unzip
mkdir -p $HOME/Android/Sdk/cmdline-tools && cd $HOME/Android/Sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest
yes | $HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "ndk;26.3.11579264"

# 또는 NDK zip 직접
wget https://dl.google.com/android/repository/android-ndk-r26d-linux.zip
unzip android-ndk-r26d-linux.zip -d $HOME/Android/Sdk/
export ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d
```

`~/.bashrc` 또는 `~/.zshrc` 에:
```sh
export ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
```

확인:
```sh
$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/*/bin/clang --version
```

---

## 경로 1. Docker (권장)

### 1.1 사전

```sh
# Ubuntu
sudo apt install docker.io && sudo systemctl start docker

# macOS
brew install colima && colima start --cpu 4 --memory 6
```

### 1.2 device BTF

```sh
adb root
bash scripts/pull_btf.sh -o vmlinux.btf
```

### 1.3 빌드 & 배포

```sh
# 이미지 빌드 + 컨테이너 안에서 전체 build (libelf 자동 포함)
make docker

# 결과 확인
ls out/aarch64/fsiotrace

# 배포
make push
adb shell '/data/local/tmp/fsiotrace -d 10 -x -o /data/local/tmp/run1'
adb pull /data/local/tmp/run1.events .
```

디버그:
```sh
make docker-shell    # 컨테이너 안 bash
```

---

## 경로 2. Ubuntu 22.04+ 직접

### 2.1 패키지

```sh
sudo apt install -y \
    build-essential clang lld llvm \
    libelf-dev zlib1g-dev libcap-dev \
    pkg-config make git python3 \
    bison flex autoconf automake libtool gawk gettext wget \
    libbz2-dev liblzma-dev libzstd-dev \
    android-tools-adb
```

### 2.2 NDK PATH 등록 (위 §사전)

### 2.3 device BTF

```sh
adb root
bash scripts/pull_btf.sh -o vmlinux.btf
```

### 2.4 빌드

```sh
make deps-check
make VMLINUX_BTF=$PWD/vmlinux.btf
```

첫 빌드 시 자동으로 `scripts/build_libelf_ndk.sh` 가 실행돼 elfutils 받아
libelf 만들고 `third_party/libelf-android/` 에 둔다. 이후엔 캐시 사용.

```sh
make push
```

---

## libelf 별도 빌드 / 재빌드

```sh
# Makefile 이 자동 처리하지만 수동 트리거:
ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d \
    bash scripts/build_libelf_ndk.sh
```

산출:
```
third_party/libelf-android/
├── include/{libelf.h,gelf.h,nlist.h,...}
└── lib/libelf.a
```

---

## 문제 해결

- **`scripts/build_libelf_ndk.sh: ANDROID_NDK_HOME not set`**:
  `export ANDROID_NDK_HOME=...` 필요
- **elfutils tarball 다운로드 실패**: 폐쇄망이면 외부망 PC 에서
  `wget https://sourceware.org/elfutils/ftp/0.191/elfutils-0.191.tar.bz2` 받아서
  본 repo 루트에 `elfutils-0.191.tar.bz2` 로 두면 wget 안 시도하고 그대로 사용.
- **obstack URL 404**: 스크립트가 폐쇄망이면 미리 `third_party/elfutils/lib/`
  에 `obstack.c`, `obstack.h` 떨궈두면 wget skip.
  외부망 URL:
  - `https://raw.githubusercontent.com/gcc-mirror/gcc/master/libiberty/obstack.c`
  - `https://raw.githubusercontent.com/gcc-mirror/gcc/master/include/obstack.h`
- **NDK clang not found**: `ANDROID_NDK_HOME` 또는 `make NDK=/path` 명시
- **BPF verifier 거부 (device)**: `dmesg | grep bpf`, BTF 가 device 것 맞는지 확인
- **SELinux 차단**: `adb shell setenforce 0` (userdebug 한정)

## 폐쇄망

[폐쇄망(offline) 환경 가이드](/fsiotrace/offline/)
