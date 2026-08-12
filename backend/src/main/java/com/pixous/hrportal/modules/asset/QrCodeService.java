package com.pixous.hrportal.modules.asset;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.common.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Generates PNG QR codes for asset tags via ZXing and stores them. */
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final StorageService storageService;

    public byte[] pngBytes(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to generate QR code");
        }
    }

    /** Generates and stores a QR for the given asset code; returns the stored path. */
    public String storeForAsset(String assetCode) {
        byte[] png = pngBytes("ASSET:" + assetCode, 240);
        return storageService.storeBytes(png, "qr", "asset_" + assetCode + ".png");
    }
}
