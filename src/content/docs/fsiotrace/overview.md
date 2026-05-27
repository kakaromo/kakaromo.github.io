---
title: fsiotrace 개요
description: Android VFS→FS→Block→UFS 경로 IO 를 한 줄 단위로 추적하는 libbpf + CO-RE eBPF tracer
---

VFS → FS(ext4/f2fs) → block → UFS(SCSI) 경로의 IO를 한 줄 단위로 추적하는
libbpf + CO-RE 기반 eBPF tracer. (소스: bpftrace repo)

- **타겟**: Android device (UFS storage, userdebug + root)
- **빌드**: Android NDK + Bionic (aarch64-linux-android). libelf 는 `scripts/build_libelf_ndk.sh` 가 elfutils 받아 자동 빌드 (Bionic 호환 패치 자동 적용)
- **권장**: Docker 컨테이너 (`make docker`)
- **호스트 직접**: Ubuntu 22.04+
- **출력**: layer별 한 줄 (명시 컬럼 + `io_flags` u64 비트마스크)

- 설계 상세: [설계 문서](/fsiotrace/design/)
- 빌드 가이드: [빌드 (NDK + Bionic)](/fsiotrace/build/)
- 사용법 / 트러블슈팅: [사용법](/fsiotrace/usage/)
- TSV 출력 형식 (Rust 분석기 연동): [TSV 출력 형식](/fsiotrace/output-format/)
- 폐쇄망 환경: [폐쇄망 환경 가이드](/fsiotrace/offline/)
- 수집된 trace 의 분석 단계: [Trace Analysis](/guide/trace-analysis/)

## Layout

```
bpftrace/
├── README.md
├── Dockerfile                    # Linux build container
├── Makefile                      # host 진입 + in-container build
├── docs/
│   ├── DESIGN.md                 # 비트 정의, hook 선정, cross-layer 전파, GKI 이슈
│   ├── BUILD.md                  # Docker / Ubuntu 네이티브 빌드 (aarch64-linux-gnu cross)
│   ├── USAGE.md                  # 시나리오별 사용법, 출력 해석, 트러블슈팅
│   ├── OUTPUT_FORMAT.md          # TSV 출력 형식 (Rust 분석기 연동)
│   └── OFFLINE.md                # 폐쇄망 환경 빌드 절차
├── scripts/
│   ├── build.sh                  # 한 줄 빌드 (NDK 감지 + BTF + libelf + make + push)
│   ├── build_libelf_ndk.sh       # libelf cross-compile (third_party/elfutils 빌드)
│   ├── bionic_libbpf_compat.h    # libbpf Bionic 호환 force-include 헤더
│   ├── android_check.sh          # device feasibility 점검
│   └── pull_btf.sh               # device BTF 받아오기
├── src/
│   ├── fsiotrace.h               # kernel/userspace 공유 (enum, io_flags, struct event)
│   ├── fsiotrace.bpf.c           # eBPF kernel side (CO-RE)
│   └── fsiotrace.c               # userspace loader + ringbuf consumer
└── third_party/
    ├── libbpf-bootstrap/         # vendored (in-tree, no submodule)
    └── elfutils/                 # vendored elfutils 0.191 + Bionic compat stubs
                                  #   (tests/, doc/, po/ 제외, ~8.5MB)
                                  #   COPYING, COPYING-GPLV2, COPYING-LGPLV3 보존
```

## 진행 단계

- [x] Phase A: device feasibility 체크 스크립트
- [x] Phase B: 빌드 환경 (Docker + NDK)
- [x] Phase C: eBPF kernel side
- [x] Phase D: userspace loader
- [ ] Phase E: 출력 포맷 확정, 외부 join 도구 (후속)

## Quick start (한 줄)

```sh
bash scripts/build.sh
```

이 한 줄이 다음을 자동으로 처리:
- NDK 경로 자동 검색 (`ANDROID_NDK_HOME` 또는 흔한 경로)
- 호스트 OS 감지 (linux-x86_64 / linux-aarch64 / darwin-x86_64)
- `vmlinux.btf` 없으면 `adb pull` 시도
- `libelf` 없으면 `build_libelf_ndk.sh` 자동 실행
- `make` 로 전체 빌드
- `adb push` (device 있을 때)

옵션:
```sh
bash scripts/build.sh --no-push           # 빌드만
bash scripts/build.sh --clean             # 청소 후 빌드
bash scripts/build.sh --ndk /path/to/ndk
bash scripts/build.sh --btf vmlinux.btf
bash scripts/build.sh --serial DEVICE
```

## Quick start (수동, 세부 제어 원할 때)

```sh
# 1) 도구
sudo apt install -y build-essential clang lld llvm \
    libelf-dev zlib1g-dev libcap-dev pkg-config make git python3 \
    bison flex autoconf automake libtool gawk gettext wget \
    libbz2-dev liblzma-dev libzstd-dev android-tools-adb

# 2) NDK
export ANDROID_NDK_HOME=$HOME/Android/Sdk/android-ndk-r26d

# 3) BTF
adb root
bash scripts/pull_btf.sh -o vmlinux.btf

# 4) libelf (한 번만)
bash scripts/build_libelf_ndk.sh

# 5) 전체 빌드 + 배포
make VMLINUX_BTF=$PWD/vmlinux.btf
make push
```

## Quick start (Docker)

```sh
make docker        # 이미지 빌드 + 컨테이너 안에서 전체 build
make push
```

## 옵션 (요약)

```
-o PREFIX        events 를 PREFIX.events 로 기록 (없으면 stdout)
-d SEC           SEC 초 후 종료
-m N             N 이벤트 후 종료
-p PID           특정 PID 만
-D MAJ:MIN       특정 block device 만
-I HEX           io_flags & MASK 인 이벤트만
-x               io_flags hex 옆에 비트 이름 풀이 [WRITE|O_SYNC|...]
--no-vfs         VFS hook off
--no-fs          FS-internal hook off
--no-blk         Block hook off
--no-ufs         UFS hook off
--wb-inode       writeback inode_ctx fallback 활성
-v               libbpf verbose
```
