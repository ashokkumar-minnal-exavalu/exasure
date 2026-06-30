package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class QuotePdfGeneratorMediator extends AbstractMediator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Color PRIMARY_COLOR =
            new Color(0, 71, 133);

    private static final Color LIGHT_GRAY =
            new Color(245, 245, 245);

    private static final Color MEDIUM_GRAY =
            new Color(200, 200, 200);

    private static final Color TEXT_GRAY =
            new Color(66, 66, 66);

    @Override
    public boolean mediate(MessageContext mc) {

        try {

            org.apache.axis2.context.MessageContext axis2MessageContext =
                    ((Axis2MessageContext) mc).getAxis2MessageContext();

            InputStream is =
                    JsonUtil.getJsonPayload(axis2MessageContext);

            String jsonPayload =
                    new String(
                            is.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            log.info("PDF INPUT PAYLOAD = " + jsonPayload);

            Map<String, Object> root =
                    MAPPER.readValue(
                            jsonPayload,
                            new TypeReference<Map<String, Object>>() {}
                    );

            byte[] pdfBytes = createPdfBytes(root);

            String base64Pdf =
                    Base64.getEncoder()
                            .encodeToString(pdfBytes);

            mc.setProperty("binaryData", base64Pdf);

            log.info("PDF GENERATED SUCCESSFULLY");

            return true;

        } catch (Exception e) {

            log.error("PDF Generation Error", e);

            return false;
        }
    }

    /* =========================================================
       PDF GENERATION
    ========================================================== */

    private byte[] createPdfBytes(
            Map<String, Object> root
    ) throws Exception {

        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream baos =
                        new ByteArrayOutputStream()
        ) {

            PDPage page =
                    new PDPage(PDRectangle.LETTER);

            document.addPage(page);

            PDPageContentStream cs =
                    new PDPageContentStream(document, page);

            float pageWidth =
                    page.getMediaBox().getWidth();

            float pageHeight =
                    page.getMediaBox().getHeight();

            float margin = 40;

            float contentWidth =
                    pageWidth - (margin * 2);

            float y = pageHeight - 40;

            /* =================================================
               HEADER
            ================================================= */

            cs.setNonStrokingColor(PRIMARY_COLOR);

            cs.addRect(
                    0,
                    pageHeight - 110,
                    pageWidth,
                    110
            );

            cs.fill();

            cs.beginText();

            cs.setFont(
                    PDType1Font.HELVETICA_BOLD,
                    26
            );

            cs.setNonStrokingColor(Color.WHITE);

            cs.newLineAtOffset(
                    margin,
                    pageHeight - 55
            );

            cs.showText("EXAVALU INSURANCE");

            cs.endText();

            String today =
                    new SimpleDateFormat(
                            "MMMM dd, yyyy"
                    ).format(new Date());

            cs.beginText();

            cs.setFont(
                    PDType1Font.HELVETICA,
                    10
            );

            cs.newLineAtOffset(
                    pageWidth - 220,
                    pageHeight - 55
            );

            cs.showText(
                    "Generated On: " + today
            );

            cs.endText();

            y = pageHeight - 140;

            /* =================================================
               SUMMARY BOX
            ================================================= */

            drawBox(
                    cs,
                    margin,
                    y - 120,
                    contentWidth,
                    120
            );

            float leftX = margin + 15;

            float rightX =
                    margin + (contentWidth / 2) + 10;

            float rowY = y - 18;

            /* LEFT COLUMN */

            writeSmall(
                    cs,
                    "Quote ID",
                    getString(root, "quoteId"),
                    leftX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Billing Account ID",
                    getString(root, "billingAccountId"),
                    leftX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Invoice ID",
                    getString(root, "invoiceId"),
                    leftX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Transaction Type",
                    getString(root, "transactionType"),
                    leftX,
                    rowY
            );

            /* RIGHT COLUMN */

            rowY = y - 18;

            writeSmall(
                    cs,
                    "Status",
                    getString(root, "status"),
                    rightX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Premium Amount",
                    getString(root, "premiumAmount")
                            + " "
                            + getString(root, "currency"),
                    rightX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Invoice Amount",
                    getString(root, "invoiceAmount")
                            + " "
                            + getString(root, "currency"),
                    rightX,
                    rowY
            );

            rowY -= 16;

            writeSmall(
                    cs,
                    "Paid Date",
                    getString(root, "paidDate"),
                    rightX,
                    rowY
            );

            y -= 150;

            /* =================================================
               PAYMENT INFORMATION
            ================================================= */

            y = drawSectionHeader(
                    cs,
                    "Payment Information",
                    margin,
                    y,
                    pageWidth
            );

            writeSmall(
                    cs,
                    "Payment ID",
                    getString(root, "paymentId"),
                    margin + 15,
                    y
            );

            y -= 16;

            writeSmall(
                    cs,
                    "Party Identifier",
                    getString(root, "partyIdentifier"),
                    margin + 15,
                    y
            );

            y -= 16;

            writeSmall(
                    cs,
                    "Currency",
                    getString(root, "currency"),
                    margin + 15,
                    y
            );

            y -= 30;

            /* =================================================
               FOOTER
            ================================================= */

            cs.beginText();

            cs.setFont(
                    PDType1Font.HELVETICA_OBLIQUE,
                    8
            );

            cs.setNonStrokingColor(TEXT_GRAY);

            cs.newLineAtOffset(margin, 25);

            cs.showText(
                    "This document is system generated by Exavalu Insurance Platform."
            );

            cs.endText();

            cs.close();

            document.save(baos);

            return baos.toByteArray();
        }
    }

    /* =========================================================
       HELPERS
    ========================================================== */

    private void drawBox(
            PDPageContentStream cs,
            float x,
            float y,
            float width,
            float height
    ) throws Exception {

        cs.setNonStrokingColor(LIGHT_GRAY);

        cs.setStrokingColor(MEDIUM_GRAY);

        cs.addRect(x, y, width, height);

        cs.fillAndStroke();
    }

    private float drawSectionHeader(
            PDPageContentStream cs,
            String title,
            float margin,
            float y,
            float pageWidth
    ) throws Exception {

        cs.setNonStrokingColor(PRIMARY_COLOR);

        cs.addRect(
                margin,
                y - 22,
                pageWidth - (margin * 2),
                22
        );

        cs.fill();

        cs.beginText();

        cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                11
        );

        cs.setNonStrokingColor(Color.WHITE);

        cs.newLineAtOffset(
                margin + 10,
                y - 15
        );

        cs.showText(title);

        cs.endText();

        return y - 35;
    }

    private void writeSmall(
            PDPageContentStream cs,
            String key,
            String value,
            float x,
            float y
    ) throws Exception {

        String safeValue =
                (key + ": "
                        + (value != null ? value : ""))
                        .replace("\n", " ")
                        .replace("\r", " ");

        cs.beginText();

        cs.setFont(
                PDType1Font.HELVETICA,
                9
        );

        cs.setNonStrokingColor(TEXT_GRAY);

        cs.newLineAtOffset(x, y);

        cs.showText(safeValue);

        cs.endText();
    }

    private String getString(
            Map<String, Object> map,
            String key
    ) {

        Object value =
                map != null
                        ? map.get(key)
                        : null;

        return value != null
                ? String.valueOf(value)
                : "";
    }
}