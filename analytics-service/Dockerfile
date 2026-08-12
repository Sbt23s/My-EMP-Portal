# Python FastAPI analytics + face-recognition + OCR service.
#
# HEAVY IMAGE WARNING: face_recognition compiles dlib from source (needs
# cmake/g++, ~10-20 min on a small EC2) and easyocr pulls in PyTorch.
# CPU-only torch is installed first to avoid the multi-GB CUDA build.
# Expect a ~4-5 GB image and a long first build. Subsequent builds are cached.

FROM python:3.11-slim
WORKDIR /app

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1

# System deps: dlib build tools, tesseract for pytesseract, OpenCV runtime libs
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        cmake \
        tesseract-ocr \
        libglib2.0-0 \
        libgl1 \
    && rm -rf /var/lib/apt/lists/*

# CPU-only PyTorch first (easyocr dependency) — saves gigabytes vs. CUDA wheels
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

# Trained face encodings live here — mounted as a named volume in compose
RUN mkdir -p /app/faces

EXPOSE 8082
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8082"]
