import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;

import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    // Set these as environment variables (never hardcode or commit them):
    //   GEMINI_API_KEY, PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET
    private static final String MY_GEMINI_KEY = System.getenv("GEMINI_API_KEY");
    private static final String MY_PAYPAL_ID = System.getenv("PAYPAL_CLIENT_ID");
    private static final String MY_PAYPAL_SECRET = System.getenv("PAYPAL_CLIENT_SECRET");

    private static final String GEMINI_MODEL = "gemini-3.8-flash";

    private static PayPalHttpClient paypalClient;

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern ORDER_ID = Pattern.compile("\\b([A-Z0-9]{12,20})\\b");

    private static boolean missing(String s) {
        return s == null || s.isBlank();
    }

    // ---------- PayPal functions (called directly by our code, no AI tool calling) ----------

    static String createPaymentOrder(double amountValue) {
        try {
            if (amountValue <= 0 || amountValue > 1000) {
                return "Invalid amount. Enter an amount between 0 and 1000.";
            }

            OrderRequest orderRequest = new OrderRequest();
            orderRequest.checkoutPaymentIntent("CAPTURE");

            ApplicationContext appContext =
                    new ApplicationContext()
                            .brandName("PayPal AI Agent")
                            .landingPage("LOGIN")
                            .userAction("PAY_NOW")
                            .returnUrl("https://example.com/success")
                            .cancelUrl("https://example.com/cancel");
            orderRequest.applicationContext(appContext);

            AmountWithBreakdown amount =
                    new AmountWithBreakdown()
                            .currencyCode("USD")
                            .value(String.format(Locale.US, "%.2f", amountValue));

            orderRequest.purchaseUnits(List.of(
                    new PurchaseUnitRequest().amountWithBreakdown(amount)
            ));

            OrdersCreateRequest request = new OrdersCreateRequest();
            request.prefer("return=representation");
            request.requestBody(orderRequest);

            HttpResponse<Order> response = paypalClient.execute(request);
            Order order = response.result();

            if (order == null || order.links() == null) {
                return "PayPal did not return an order or approval link.";
            }

            String approvalLink = order.links().stream()
                    .filter(link -> "approve".equalsIgnoreCase(link.rel())
                            || "payer-action".equalsIgnoreCase(link.rel()))
                    .map(LinkDescription::href)
                    .filter(href -> href != null && !href.isBlank())
                    .findFirst()
                    .orElse(null);

            if (approvalLink == null) {
                return "Order created, but no approval link was returned. Order ID: " + order.id();
            }

            return "Sandbox order created successfully!\n"
                    + "Order ID: " + order.id() + "\n"
                    + "Approval link: " + approvalLink + "\n"
                    + "Open the link, log in with a Sandbox buyer account and approve, "
                    + "then type: capture " + order.id();

        } catch (Exception e) {
            System.err.println("PAYPAL ORDER ERROR:");
            e.printStackTrace();
            return "Payment failed: " + e.getMessage();
        }
    }

    static String captureOrder(String orderId) {
        try {
            OrdersCaptureRequest request = new OrdersCaptureRequest(orderId.trim());
            request.requestBody(new OrderRequest());

            HttpResponse<Order> response = paypalClient.execute(request);
            Order order = response.result();

            return "Capture result for order " + order.id() + ": status = " + order.status();

        } catch (Exception e) {
            System.err.println("PAYPAL CAPTURE ERROR:");
            e.printStackTrace();
            return "Capture failed (was the order approved by the buyer first?): " + e.getMessage();
        }
    }

    // ---------- Main loop ----------

    public static void main(String[] args) {

        if (missing(MY_GEMINI_KEY) || missing(MY_PAYPAL_ID) || missing(MY_PAYPAL_SECRET)) {
            throw new IllegalStateException(
                    "Missing credentials. Set GEMINI_API_KEY, PAYPAL_CLIENT_ID "
                            + "and PAYPAL_CLIENT_SECRET environment variables.");
        }

        try {
            paypalClient = new PayPalHttpClient(
                    new PayPalEnvironment.Sandbox(MY_PAYPAL_ID.trim(), MY_PAYPAL_SECRET.trim()));

            // Test PayPal alone:  run with argument  --test
            if (args.length > 0 && "--test".equals(args[0])) {
                System.out.println(createPaymentOrder(5.00));
                return;
            }

            // Gemini is used ONLY for normal chat (no tool calling => no thought_signature problem).
            GoogleAiGeminiChatModel model =
                    GoogleAiGeminiChatModel.builder()
                            .apiKey(MY_GEMINI_KEY.trim())
                            .modelName(GEMINI_MODEL)
                            .build();

            System.out.println("================================");
            System.out.println(" PayPal AI Agent is READY");
            System.out.println(" Environment: SANDBOX");
            System.out.println(" Model: " + GEMINI_MODEL);
            System.out.println(" Examples:");
            System.out.println("   create payment 10 USD");
            System.out.println("   capture <ORDER_ID>");
            System.out.println(" Type exit to quit");
            System.out.println("================================");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nUser: ");
                    if (!scanner.hasNextLine()) break;

                    String input = scanner.nextLine().trim();
                    if (input.isEmpty()) continue;
                    if ("exit".equalsIgnoreCase(input)) break;

                    String lower = input.toLowerCase(Locale.ROOT);

                    try {
                        // 1) Capture an order
                        if (lower.contains("capture")) {
                            Matcher m = ORDER_ID.matcher(input);
                            if (m.find()) {
                                System.out.println("\nAI Agent: " + captureOrder(m.group(1)));
                            } else {
                                System.out.println("\nAI Agent: Please give the Order ID, "
                                        + "for example: capture 5O190127TN364715T");
                            }
                            continue;
                        }

                        // 2) Create a payment order
                        boolean wantsPayment = lower.contains("payment") || lower.contains("order")
                                || lower.contains("pay") || lower.contains("link")
                                || lower.contains("invoice");

                        if (wantsPayment) {
                            Matcher m = AMOUNT.matcher(input);
                            if (!m.find()) {
                                System.out.println("\nAI Agent: Please tell me the amount, "
                                        + "for example: create payment 10 USD");
                                continue;
                            }
                            double amount = Double.parseDouble(m.group(1));

                            System.out.print("\nAI Agent: Create a Sandbox order for $"
                                    + String.format(Locale.US, "%.2f", amount)
                                    + " USD? (yes/no): ");
                            String confirm = scanner.hasNextLine() ? scanner.nextLine().trim() : "no";

                            if ("yes".equalsIgnoreCase(confirm) || "y".equalsIgnoreCase(confirm)) {
                                System.out.println("\nAI Agent: " + createPaymentOrder(amount));
                            } else {
                                System.out.println("\nAI Agent: Cancelled.");
                            }
                            continue;
                        }

                        // 3) Everything else: normal chat with Gemini (no tools)
                        String reply = model.chat(input);
                        System.out.println("\nAI Agent: " + reply);

                    } catch (Exception e) {
                        System.err.println("AGENT ERROR:");
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("APPLICATION STARTUP ERROR:");
            e.printStackTrace();
        }
    }
}