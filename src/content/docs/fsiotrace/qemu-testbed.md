---
title: QEMU 테스트베드 (기기 없이 검증)
description: GKI 6.6 커널 + UFS 에뮬레이션으로 실기기 없이 verifier·attach·4레이어 이벤트·ftrace 대조까지 검증하는 절차
---
> **정본**: repo 의 `scripts/qemu/README.md`. 이 페이지는 렌더용 사본이며
> 스크립트 경로는 bpftrace repo 기준이다.

GKI 6.6 커널을 QEMU 로 띄우고 **UFS 에뮬레이션**(QEMU 7.2+ 의 UFSHCI 3.0)을
붙여, 실기기 없이 fsiotrace 를 끝까지 돌려본다.

**여기서 확인되는 것**: verifier 통과, attach 성공, VFS/FS/BLK/UFS 4개 레이어
이벤트, ftrace 와의 카운트 대조, 풀패스 출력, f2fs 세분 플래그.
실제로 이 환경에서 verifier 거부 2건과 동작 버그 여러 건을 잡았다.

**여기서 확인 안 되는 것**: 삼성 vendor hook(`android_vh_ufs_*`) 경로,
MCQ(`mcq=on` 을 줘도 QEMU 가 legacy SDB 로 fallback), 실물 UFS 타이밍.

## 준비물 (repo 에 없음 — 용량 때문에 gitignore)

1. **호스트 QEMU 11+**: `brew install qemu`
   (Ubuntu 22.04 apt 는 6.2 라 UFS 디바이스가 없다. 컨테이너 말고 호스트에서 실행)
2. **삼성 오픈소스 커널 + 툴체인** → `samsung_img/` 에 풀어둔다.
   자세한 절차와 함정은 삼성 커널 BTF 빌드 메모 참고. 핵심만:
   - **반드시 case-sensitive 볼륨에 풀 것.** 커널 트리에 case 만 다른 파일이
     26쌍 있어(netfilter `xt_dscp.c` ↔ `xt_DSCP.c` 등) macOS 기본 APFS 에선
     서로 덮어써서 빌드가 깨진다.
     `diskutil apfs addVolume disk3 "Case-sensitive APFS" kbuild`
   - x86_64 툴체인이라 Docker 는 `--platform linux/amd64`.

## 순서

```sh
KB=/Volumes/kbuild          # 커널 소스를 푼 case-sensitive 볼륨
W=samsung_img/qemu_work     # 산출물 디렉토리

# 1) 컨테이너 이미지
docker build --platform linux/amd64 -t fsiotrace-kbuild -f scripts/qemu/Dockerfile.kbuild .
docker build --platform linux/amd64 -t fsiotrace-qemu   -f scripts/qemu/Dockerfile.qemu   .

# 2) 게스트 커널 (QEMU 부팅용 — gki_defconfig 는 Android init 전제라 그냥은 안 뜬다)
cp scripts/qemu/build_qemu_kernel.sh $KB/
docker run --rm --platform linux/amd64 -v "$KB:/src" fsiotrace-kbuild bash /src/build_qemu_kernel.sh

# 3) 그 커널의 BTF 추출
docker run --rm --platform linux/amd64 -v "$KB:/kb" fsiotrace-kbuild bash -c '
  export PATH=/kb/kernel_platform/prebuilts/clang/host/linux-x86/clang-r510928/bin:$PATH
  llvm-objcopy --dump-section .BTF=/kb/vmlinux_qemu.btf /kb/out_qemu/vmlinux'

# 4) 게스트 바이너리 (fsiotrace + fio, glibc/static aarch64) + initramfs
cp scripts/qemu/*.sh $W/
docker run --rm --platform linux/amd64 -v "$PWD:/proj" -v "$PWD/$W:/work" -v "$KB:/kb" \
  fsiotrace-qemu bash /work/build_qemu_guest.sh
docker run --rm --platform linux/amd64 -v "$PWD/$W:/work" fsiotrace-qemu bash /work/mk_initramfs.sh

# 5) 디스크 2개: LU0=ext4(/data), LU1=f2fs(/f2fs)
qemu-img create -f raw $W/ufs0.img 2G
qemu-img create -f raw $W/f2fs0.img 1G
docker run --rm --platform linux/amd64 -v "$PWD/$W:/work" fsiotrace-qemu bash -c '
  apt-get update -qq && apt-get install -y -qq e2fsprogs f2fs-tools
  mke2fs -t ext4 -q -F -b 4096 /work/ufs0.img
  mkfs.f2fs -f /work/f2fs0.img'

# 6) 실행
bash scripts/qemu/run_qemu.sh          # KBUILD=... QEMU_WORK=... 로 경로 변경 가능
```

VM 안에서 `sh /bin/<test>.sh` (테스트 스크립트를 initramfs 에 넣어둔 경우).
비대화형: `(sleep 22; echo "sh /bin/fiotest.sh"; sleep 200; echo "poweroff -f") | bash scripts/qemu/run_qemu.sh`

## tests/

| 스크립트 | 용도 |
|---|---|
| `fiotest.sh`  | fio 랜덤 IO 로 UFS send/complete 짝 검증 + ftrace 대조 |
| `chain.sh`    | VFS→FS→BLK→UFS 로 comm/filename 이 유실 없이 내려가는지 |
| `blk.sh`      | block 계층 이벤트 손실 대조 |
| `f2fstest.sh` | f2fs node/data/meta, hot/warm/cold 플래그 검증 (LU1 필요) |

## 측정할 때 반드시 지킬 것

- **ftrace 버퍼를 키운다.** 기본 7KB/cpu 라 고부하에선 ftrace 쪽이 먼저 넘쳐
  "손실이 음수" 로 나온다. `echo 65536 > /sys/kernel/tracing/buffer_size_kb`
  후 `per_cpu/*/stats` 의 `overrun` 이 0 인지 확인.
- **측정 창을 맞춘다.** fsiotrace attach 완료를 기다린 뒤 ftrace 를 켜고,
  ftrace 를 먼저 끈 다음 fsiotrace 를 종료한다. 안 맞추면 ±수 건이 흔들리는데
  이건 유실이 아니라 창 오차다(부호가 음수로 뒤집히면 확실).
- **IO 를 끝내고 몇 초 쉰 뒤 종료한다.** 트레이서가 IO 한복판에서 끝나면
  in-flight 만큼 send 가 많게 나오는 게 정상이다.
