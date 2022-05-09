package th.ac.kmitl.se;

import org.graphwalker.core.machine.ExecutionContext;
import org.graphwalker.java.annotation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.*;
import static org.mockito.Mockito.*;

// Update the filename of the saved file of your model here.
@Model(file  = "model.json")
public class OrderAdapter extends ExecutionContext {
    // The following method add some delay between each step
    // so that we can see the progress in GraphWalker player.
    public static int delay = 0;
    @AfterElement
    public void afterEachStep() {
        try
        {
            Thread.sleep(delay);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    OrderDB orderDB;
    ProductDB productDB;
    PaymentService paymentService;
    ShippingService shippingService;
    Order order;
    Address address;
    Card card;
    @BeforeExecution
    public void setUp() {
        // Add the code here to be executed before
        // GraphWalk starts traversing the model.

        this.orderDB = mock(OrderDB.class);
        this.productDB = mock(ProductDB.class);
        this.paymentService = spy(PaymentService.class);
        this.shippingService = spy(ShippingService.class);

        this.order = new Order(orderDB, productDB, paymentService, shippingService);

        this.address = new Address("", "1011 Lincoln St", "", "", "Linden", "07036");
        this.card = new Card("111", "John Doe", 10, 2024);

        when(this.orderDB.getOrderID()).thenReturn(1);
        when(this.orderDB.retrieveOrder(1)).thenReturn(this.order);
        when(this.productDB.getPrice("Apple Watch")).thenReturn(1500f);
        when(this.productDB.getWeight("Apple Watch")).thenReturn(350f);
        when(this.shippingService.getPrice(address, 700f)).thenReturn(50f);
        when(this.shippingService.ship(address, 700f)).thenReturn("ABC123");
    }

    @Edge()
    public void reset() {
        System.out.println("Edge reset");
        // Reset everything.
        this.setUp();
    }

    @Edge()
    public void place() {
        System.out.println("Edge place");

        // Place an order.
        this.order.place("John", "Apple Watch", 2, this.address);
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.PLACED, this.order.getStatus());
    }

    @Edge()
    public void cancel() {
        System.out.println("Edge cancel");

        Order.Status oldStatus = this.order.getStatus();

        // Cancel the order.
        this.order.cancel();

        if (oldStatus == Order.Status.PLACED) {
            verify(this.orderDB).update(this.order);
            clearInvocations(this.orderDB);
            assertEquals(Order.Status.CANCELED, this.order.getStatus());
            return;
        }

        ArgumentCaptor<String> codeCallback = ArgumentCaptor.forClass(String.class);
        verify(this.paymentService).refund(eq("SUC123"), any());
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.PAID, oldStatus);
        assertEquals(Order.Status.AWAIT_REFUND, this.order.getStatus());
    }

    @Edge()
    public void pay() {
        System.out.println("Edge pay");

        // Pay the order.
        this.order.pay(this.card);
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        verify(this.paymentService).pay(eq(this.card), eq(3050f), any());

        assertEquals(Order.Status.PAYMENT_CHECK, this.order.getStatus());
    }

    @Edge()
    public void retryPay() {
        System.out.println("Edge retryPay");

        // Retry to pay.
        this.order.pay(card);
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        verify(this.paymentService).pay(eq(this.card), eq(3050f), any());

        assertEquals(Order.Status.PAYMENT_CHECK, this.order.getStatus());
    }

    @Edge()
    public void paySuccess() {
        System.out.println("Edge paySuccess");

        ArgumentCaptor<PaymentCallback> paymentCallback = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(this.paymentService).pay(eq(this.card), eq(3050f), paymentCallback.capture());
        clearInvocations(this.paymentService);

        // Trigger onSuccess.
        paymentCallback.getValue().onSuccess("SUC123");
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.PAID, this.order.getStatus());
        assertEquals("SUC123", this.order.paymentConfirmCode);
    }

    @Edge()
    public void payError() {
        System.out.println("Edge payError");

        ArgumentCaptor<PaymentCallback> paymentCallback = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(this.paymentService).pay(eq(this.card), eq(3050f), paymentCallback.capture());
        clearInvocations(this.paymentService);

        // Trigger onError.
        paymentCallback.getValue().onError("ERR123");
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.PAYMENT_ERROR, this.order.getStatus());
    }

    @Edge()
    public void ship() {
        System.out.println("Edge ship");

        // Ship the order.
        this.order.ship();
        verify(this.shippingService).ship(eq(this.address), eq(700f));
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.SHIPPED, this.order.getStatus());
        assertEquals("ABC123", this.order.trackingCode);
    }

    @Edge()
    public void refundSuccess() {
        System.out.println("Edge refundSuccess");

        ArgumentCaptor<PaymentCallback> paymentCallback = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(this.paymentService).refund(eq("SUC123"), paymentCallback.capture());
        clearInvocations(this.paymentService);

        // Trigger onSuccess.
        paymentCallback.getValue().onSuccess("SUC123");
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.REFUNDED, this.order.getStatus());
    }

    @Edge()
    public void refundError() {
        System.out.println("Edge refundError");

        ArgumentCaptor<PaymentCallback> paymentCallback = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(this.paymentService).refund(eq("SUC123"), paymentCallback.capture());

        // Trigger onError.
        paymentCallback.getValue().onError("SUC123");
        verify(this.orderDB).update(this.order);
        clearInvocations(this.orderDB);

        assertEquals(Order.Status.REFUND_ERROR, this.order.getStatus());
    }
}
