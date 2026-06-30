package com.example.wso2.pdf;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.commons.json.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class PdfGeneratorMediator extends AbstractMediator {

    // ================= PAGE CONSTANTS =================
    private static final float PAGE_TOP = 770;
    private static final float PAGE_BOTTOM = 60;

    private static final float LEFT = 50;
    private static final float RIGHT = 545;

    private static final float COL_LABEL = 55;
    private static final float COL_VALUE = 260;

    private static final float ROW_HEIGHT = 18;

    // ================= COLORS =================
    private static final Color HEADER_BG = new Color(30, 58, 138);     // Deep blue
    private static final Color FOOTER_BG = new Color(229, 231, 235);   // Light gray

    // ================= PDF OBJECTS =================
    private PDDocument document;
    private PDPageContentStream content;
    private float y;

    private String currentQuoteId;

    @Override
    public boolean mediate(MessageContext mc) {

        try {
            // ================= READ JSON =================
            org.apache.axis2.context.MessageContext axis2MC =
                    ((Axis2MessageContext) mc).getAxis2MessageContext();

            String jsonPayload = JsonUtil.jsonPayloadToString(axis2MC);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonPayload);

            currentQuoteId = root.path("quoteId").asText();

            // ================= INIT PDF =================
            document = new PDDocument();
            createNewPage();

            drawHeader(
                    "EXAVALU INSURANCE",
                    "Automobile Insurance",
                    currentQuoteId
            );

            // ================= QUOTE SUMMARY =================
            drawSection("QUOTE SUMMARY");
            drawRow("Quote ID", root.path("quoteId").asText());
            drawRow("Status", root.path("status").asText());
            drawRow("Product", root.path("productName").asText());
            drawRow("Policy Period",
                    root.path("policyStartDate").asText() + " -> " +
                            root.path("policyEndDate").asText());
            drawRow("Sales Channel", root.path("salesChannel").asText());
            drawRow("Currency", root.path("currency").asText());
            drawRow("Total Premium", root.path("totalPremium").asText());
            drawRow("Gross Fees", root.path("grossFees").asText());

            // ================= DRIVERS =================
            drawSection("DRIVERS");
            for (JsonNode d : root.path("Drivers")) {
                drawRow("Designation", d.path("driverDesignation").asText());
                drawRow("Name",
                        d.path("driverFirstname").asText() + " " +
                                d.path("driverLastname").asText());
                drawRow("License",
                        d.path("driverLicenseState").asText() + " - " +
                                d.path("driverLicenseNumber").asText());
                spacer();
            }

            // ================= PRELIMINARY QUESTIONS =================
            drawSection("PRELIMINARY QUESTIONS");
            root.path("preliminary-questions").fields()
                    .forEachRemaining(e ->
                            drawRow(e.getKey(), e.getValue().asText()));

            // ================= VEHICLE DETAILS =================
            drawSection("VEHICLE DETAILS");
            int vIndex = 1;

            for (JsonNode v : root.path("vehicleDetails").path("vehicle")) {
                drawRow("Vehicle #" + vIndex++, "");
                drawRow("Type", v.path("vehicleType").asText());
                drawRow("Make / Model",
                        v.path("vehicleMake").asText() + " " +
                                v.path("vehicleModel").asText());
                drawRow("Year", v.path("vehicleYear").asText());
                drawRow("Usage", v.path("primaryVehicleUse").asText());
                drawRow("License",
                        v.path("licenseState").asText() + " - " +
                                v.path("licensePlateNumber").asText());

                for (JsonNode c : v.path("coverages")) {
                    JsonNode lim = c.path("coverangeLimits");
                    drawRow("Coverage - " + c.path("name").asText(),
                            lim.path("coveragePerItem").asText() + " / " +
                                    lim.path("coveragePerEvent").asText() + " / " +
                                    lim.path("coverageInAggregate").asText());
                }
                spacer();
            }

            // ================= FEES =================
            drawSection("FEES");
            for (JsonNode f : root.path("fees")) {
                drawRow(f.path("name").asText(), f.path("amount").asText());
            }

            // ================= FOOTER =================
            drawFooter();

            content.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            document.close();

            mc.setProperty(
                    "quoteBinaryData",
                    Base64.getEncoder().encodeToString(baos.toByteArray())
            );

            return true;

        } catch (Exception e) {
            log.error("PDF generation failed", e);
            return false;
        }
    }

    // ==========================================================
    // PAGE MANAGEMENT
    // ==========================================================
    private void createNewPage() {
        try {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_TOP;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureSpace(float requiredHeight) {
        try {
            if (y - requiredHeight < PAGE_BOTTOM) {
                drawFooter();
                content.close();
                createNewPage();
                drawHeader(
                        "EXAVALU INSURANCE",
                        "Automobile Insurance",
                        currentQuoteId
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // COLORED HEADER
    // ==========================================================
    private void drawHeader(String companyName,
                            String documentTitle,
                            String quoteId) {

        try {
            String today = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));

            // Background rectangle
            content.setNonStrokingColor(HEADER_BG);
            content.addRect(0, 740, PDRectangle.A4.getWidth(), 100);
            content.fill();

            // Text color
            content.setNonStrokingColor(Color.WHITE);

            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 16);
            content.newLineAtOffset(LEFT, 800);
            content.showText(sanitize(companyName));
            content.endText();

            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 12);
            content.newLineAtOffset(LEFT, 780);
            content.showText(sanitize(documentTitle));
            content.endText();

            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 10);
            content.newLineAtOffset(360, 800);
            content.showText("Date : " + today);
            content.endText();

            content.beginText();
            content.newLineAtOffset(360, 780);
            content.showText("Quote ID : " + sanitize(quoteId));
            content.endText();

            // Reset text color
            content.setNonStrokingColor(Color.BLACK);

            y = 720;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // FOOTER
    // ==========================================================
    private void drawFooter() {
        try {
            content.setNonStrokingColor(FOOTER_BG);
            content.addRect(0, 0, PDRectangle.A4.getWidth(), 45);
            content.fill();

            content.setNonStrokingColor(Color.DARK_GRAY);
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 9);
            content.newLineAtOffset(LEFT, 20);
            content.showText("© " + LocalDate.now().getYear() +
                    " Exavalu Insurance. Confidential.");
            content.endText();

            content.setNonStrokingColor(Color.BLACK);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // SECTION
    // ==========================================================
    private void drawSection(String title) {
        try {
            ensureSpace(40);

            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 13);
            content.newLineAtOffset(LEFT, y);
            content.showText(sanitize(title));
            content.endText();

            y -= 6;
            content.moveTo(LEFT, y);
            content.lineTo(RIGHT, y);
            content.stroke();

            y -= 18;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // ROW
    // ==========================================================
    private void drawRow(String label, String value) {
        try {
            ensureSpace(ROW_HEIGHT);

            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 10);
            content.newLineAtOffset(COL_LABEL, y);
            content.showText(sanitize(label));
            content.endText();

            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 10);
            content.newLineAtOffset(COL_VALUE, y);
            content.showText(sanitize(value));
            content.endText();

            y -= ROW_HEIGHT;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void spacer() {
        try {
            ensureSpace(10);
            y -= 8;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // UNICODE SAFETY
    // ==========================================================
    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("→", "->")
                .replaceAll("[^\\x00-\\x7F]", "");
    }
}
