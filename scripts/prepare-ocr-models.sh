#!/usr/bin/env bash
set -euo pipefail

MODEL_ROOT="paddle-sdk/src/main/assets/models"
DET_DIR="$MODEL_ROOT/det"
REC_DIR="$MODEL_ROOT/rec"
mkdir -p "$DET_DIR" "$REC_DIR" audit

DET_URL="https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx?download=true"
REC_URL="https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/main/inference.onnx?download=true"
REC_YML_URL="https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/main/inference.yml?download=true"

download() {
  local url="$1"
  local target="$2"
  curl --fail --location --retry 4 --retry-all-errors --connect-timeout 30 \
    --output "$target.part" "$url"
  mv "$target.part" "$target"
}

download "$DET_URL" "$DET_DIR/inference.onnx"
download "$REC_URL" "$REC_DIR/inference.onnx"
download "$REC_YML_URL" "$REC_DIR/inference.yml"

echo "eb13b44b25bb36f89528b68720af8a61d9cf381176107f465db1757b65d086e1  $DET_DIR/inference.onnx" | sha256sum --check -
echo "9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba  $REC_DIR/inference.onnx" | sha256sum --check -

sha256sum "$DET_DIR/inference.onnx" "$REC_DIR/inference.onnx" "$REC_DIR/inference.yml" \
  > audit/ocr-model-sha256.txt
{
  echo "engine=PP-OCRv6_medium"
  echo "detection=$DET_URL"
  echo "recognition=$REC_URL"
  echo "recognition_config=$REC_YML_URL"
  echo "runtime=ONNX Runtime Android 1.21.1"
} > audit/ocr-model-provenance.txt
