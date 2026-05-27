---
title: 폐쇄망(offline) 환경 가이드
description: 외부망 차단 환경에서 NDK sysroot 만으로 fsiotrace 를 빌드하는 절차
---

NDK + Bionic 빌드 흐름 기준. **arm64 apt 패키지에 의존하지 않는다** —
사용자 환경상 폐쇄망뿐 아니라 외부망 PC 도 ports.ubuntu.com 같은 arm64
미러 접근이 안 될 수 있어서 NDK 자체 sysroot 만 사용한다.

폐쇄망 전략은 두 가지. 1번이 가장 견고.

### 전략 1 (강력 추천): 외부망에서 libelf 도 미리 빌드해서 결과만 가져오기

외부망 PC 에서 `scripts/build_libelf_ndk.sh` 를 한 번 돌려 만들어진
`third_party/libelf-android/` 디렉토리까지 commit 또는 tarball 에 포함시킨다.
폐쇄망 PC 는 build_libelf_ndk.sh 를 다시 돌리지 않아도 됨 (Makefile 이
파일 존재 확인 후 skip).

```sh
# 외부망 PC
git clone https://github.com/kakaromo/bpftrace
cd bpftrace
export ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d
bash scripts/build_libelf_ndk.sh
ls third_party/libelf-android/lib/libelf.a    # 확인

# 결과를 같이 묶기
tar czf bpftrace-offline.tgz bpftrace android-ndk-r26d-linux.zip
```

폐쇄망에서는 build_libelf_ndk.sh 안 돌고 바로 `make docker` / `make` 진행.

### 전략 2: 외부 파일을 묶어 옮기고 폐쇄망에서 빌드

build_libelf_ndk.sh 를 폐쇄망에서 처음으로 돌릴 때 인터넷 접근이 없으므로,
스크립트가 받으려는 파일을 미리 정확한 위치에 박아두면 wget 을 skip 한다.

## 외부망에서 준비

```sh
git clone https://github.com/kakaromo/bpftrace
cd bpftrace

# 1) Android NDK
wget https://dl.google.com/android/repository/android-ndk-r26d-linux.zip

# 2) elfutils tarball (build_libelf_ndk.sh 가 이 파일 발견하면 wget skip)
wget https://sourceware.org/elfutils/ftp/0.191/elfutils-0.191.tar.bz2

# 3) obstack 소스 (build_libelf_ndk.sh 가 third_party/elfutils/lib/ 에 있으면 skip)
mkdir -p third_party/elfutils/lib   # 이 디렉토리는 elfutils 압축 풀면 생기지만 미리 만들어 둠
wget -O third_party/elfutils/lib/obstack.c \
    https://raw.githubusercontent.com/gcc-mirror/gcc/master/libiberty/obstack.c
wget -O third_party/elfutils/lib/obstack.h \
    https://raw.githubusercontent.com/gcc-mirror/gcc/master/include/obstack.h

# 4) Docker 경로면 이미지도 미리 빌드해서 저장
docker build -t fsiotrace-build:latest -f Dockerfile .
docker save fsiotrace-build:latest | gzip > fsiotrace-build.tgz

# 5) (옵션) host 직접 빌드면 apt 패키지 .deb 도 받아둠. amd64 만, arm64 의존 없음.
mkdir -p debs && cd debs
apt download build-essential clang lld llvm libelf-dev zlib1g-dev libcap-dev \
    pkg-config make git python3 bison flex autoconf automake libtool gawk \
    gettext libbz2-dev liblzma-dev libzstd-dev wget android-tools-adb \
    $(apt-cache depends --recurse --no-recommends --no-suggests \
        --no-conflicts --no-breaks --no-replaces --no-enhances --no-pre-depends \
        clang llvm libelf-dev | grep '^\w' | sort -u)
cd ..
# NOTE: libelf-dev:arm64 같은 arm64 패키지는 받지 않는다. libelf 는 NDK 로
# 직접 빌드 (build_libelf_ndk.sh). host libelf-dev (amd64) 는 bpftool 빌드에만 필요.

# 6) 모든 것 한 묶음
tar czf bpftrace-offline.tgz bpftrace android-ndk-r26d-linux.zip \
    fsiotrace-build.tgz debs/
# USB 또는 내부 공유로 폐쇄망 이동
```

## 폐쇄망 PC

```sh
tar xzf bpftrace-offline.tgz
unzip android-ndk-r26d-linux.zip -d $HOME/Android/Sdk/
export ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d
echo "export ANDROID_NDK_HOME=$ANDROID_NDK_HOME" >> ~/.bashrc

# Docker 경로
gunzip -c fsiotrace-build.tgz | docker load
cd bpftrace
adb root
bash scripts/pull_btf.sh -o vmlinux.btf
make docker

# 호스트 직접 경로
sudo dpkg -i debs/*.deb && sudo apt-get install -f
cd bpftrace
adb root
bash scripts/pull_btf.sh -o vmlinux.btf
make deps-check
make VMLINUX_BTF=$PWD/vmlinux.btf
```

build_libelf_ndk.sh 는 폐쇄망에서도 wget 안 시도. 위 3단계에서 미리 박아둔
tarball / obstack.c / obstack.h 를 그대로 사용.

## 소스 갱신

```sh
# 외부망
git pull
git clone --recurse-submodules https://github.com/libbpf/libbpf-bootstrap /tmp/lbb
cd /tmp/lbb && find . -name '.git*' -exec rm -rf {} +
rsync -a --delete /tmp/lbb/ <bpftrace>/third_party/libbpf-bootstrap/
```

## 트러블슈팅

- **`elfutils-0.191.tar.bz2 not found, downloading...`**:
  스크립트가 wget 시도 = 외부망 막힘 = 사전 준비 빠짐. tarball 을
  repo 루트에 직접 두기.
- **`obstack URL 404`**: 마찬가지. obstack.c / obstack.h 를
  `third_party/elfutils/lib/` 에 미리 둘 것.
- **`docker load` platform 경고**: 빌드 호스트와 폐쇄망 호스트의 architecture
  다름. Dockerfile 은 amd64 가정.
- **NDK 경로 안 맞음**: `~/.bashrc` 에 `ANDROID_NDK_HOME` 등록 필수.
