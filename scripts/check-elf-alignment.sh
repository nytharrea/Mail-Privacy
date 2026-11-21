#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <library.so | directory | package.apk>..." >&2
  exit 2
fi

OBJDUMP="${OBJDUMP:-}"
if [ -z "${OBJDUMP}" ] && [ -n "${ANDROID_NDK_HOME:-}" ]; then
  OBJDUMP="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump"
fi
if [ -z "${OBJDUMP}" ]; then
  OBJDUMP="$(command -v llvm-objdump || command -v objdump || true)"
fi
if [ -z "${OBJDUMP}" ] || [ ! -x "${OBJDUMP}" ]; then
  echo "ERROR - no objdump found; set OBJDUMP or ANDROID_NDK_HOME" >&2
  exit 2
fi

workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

libraries=()

collect_from_directory() {
  while IFS= read -r -d '' so_file; do
    libraries+=("${so_file}")
  done < <(find "$1" -type f -name '*.so' -print0)
}

for input in "$@"; do
  if [ -d "${input}" ]; then
    collect_from_directory "${input}"
  elif [[ "${input}" == *.apk ]] && [ -f "${input}" ]; then
    target="${workdir}/$(basename "${input}")"
    mkdir -p "${target}"
    unzip -q -o "${input}" 'lib/*' -d "${target}" || [ "$?" -eq 11 ]
    collect_from_directory "${target}"
  elif [ -f "${input}" ]; then
    libraries+=("${input}")
  else
    echo "ERROR - ${input} does not exist" >&2
    exit 1
  fi
done

if [ "${#libraries[@]}" -eq 0 ]; then
  echo "ERROR - no native shared libraries found" >&2
  exit 1
fi

status=0
for so_file in "${libraries[@]}"; do
  echo "Checking 16 KB ELF LOAD alignment for ${so_file}"
  load_headers="$("${OBJDUMP}" -p "${so_file}" | awk '/LOAD/ { print $NF }')"
  if [ -z "${load_headers}" ]; then
    echo "ERROR - no ELF LOAD segments found in ${so_file}" >&2
    status=1
    continue
  fi
  while IFS= read -r alignment; do
    if ! [[ "${alignment}" =~ ^2\*\*(14|15|16)$ ]]; then
      echo "ERROR - ${so_file} has unsupported LOAD alignment: ${alignment}" >&2
      status=1
    fi
  done <<< "${load_headers}"
done

exit "${status}"
