import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Computer Service Centre Management System - Swing UI (in-memory data, mirrors the Eraser schema). */
public class ServiceCentreApp extends JFrame {
    static final String[] ST = {"RECEIVED", "DIAGNOSING", "WAITING_PARTS", "REPAIRING", "COMPLETED", "DELIVERED", "CANCELLED"};
    static final String[] DEV = {"LAPTOP", "DESKTOP", "PRINTER", "MONITOR", "PHONE", "OTHER"};
    static final String[] PRI = {"LOW", "NORMAL", "HIGH"};
    static final String[] PAY = {"CASH", "CARD", "UPI"};

    // one model per table in the schema (requests + tickets are shown together)
    DefaultTableModel tk = mk("Ticket", "Customer", "Phone", "Device", "Problem", "Status", "Priority", "Engineer", "Created");
    DefaultTableModel lg = mk("Ticket", "From", "To", "By", "When");
    DefaultTableModel inv = mk("ID", "SKU", "Name", "Category", "Price", "Stock", "Reorder at");
    DefaultTableModel bill = mk("Invoice", "Ticket", "Parts", "Labour", "Discount", "Tax", "Total", "Status", "Method", "Issued");
    DefaultTableModel staff = mk("ID", "Name", "Designation", "Specialization", "Phone", "Email");
    List<double[]> items = new ArrayList<>(); // ticket_items: {ticketId, itemId, qty, unitPrice}
    JComboBox<String> me = new JComboBox<>();
    JTable tkT, invT, billT, staffT;

    static DefaultTableModel mk(String... cols) {
        return new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }
    static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
    static String money(double d) { return String.format("%.2f", d); }
    static double num(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; } }
    static int nextId(DefaultTableModel m) { return m.getRowCount() == 0 ? 1 : (int) m.getValueAt(m.getRowCount() - 1, 0) + 1; }
    static int rowOf(DefaultTableModel m, int id) {
        for (int i = 0; i < m.getRowCount(); i++) if ((int) m.getValueAt(i, 0) == id) return i;
        return -1;
    }

    public ServiceCentreApp() {
        super("Computer Service Centre Management System");
        seed();
        for (int i = 0; i < staff.getRowCount(); i++) me.addItem((String) staff.getValueAt(i, 1));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(new JLabel("Signed in as:"));
        top.add(me);

        tkT = new JTable(tk); invT = new JTable(inv); billT = new JTable(bill); staffT = new JTable(staff);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Tickets", tab(tkT, btn("New request", e -> newRequest()), btn("Change status", e -> changeStatus()),
                btn("Add part used", e -> addPart()), btn("Create invoice", e -> makeInvoice()), btn("View history", e -> history())));
        tabs.add("Inventory", tab(invT, btn("Add item", e -> newItem()), btn("Add stock", e -> restock())));
        tabs.add("Invoices", tab(billT, btn("Mark as paid", e -> pay())));
        tabs.add("Staff", tab(staffT, btn("Add staff member", e -> newStaff())));

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 600);
        setLocationRelativeTo(null);
    }

    JButton btn(String t, java.awt.event.ActionListener a) { JButton b = new JButton(t); b.addActionListener(a); return b; }

    JPanel tab(JTable t, JButton... bs) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton b : bs) bar.add(b);
        t.setRowHeight(24);
        t.setAutoCreateRowSorter(true);
        JPanel p = new JPanel(new BorderLayout());
        p.add(bar, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    boolean ask(String title, String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridLayout(labels.length, 2, 6, 6));
        for (int i = 0; i < labels.length; i++) { p.add(new JLabel(labels[i])); p.add(fields[i]); }
        return JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** Returns the id in column 0 of the selected row, or -1 after telling the user what to do. */
    int selected(JTable t, String what) {
        int r = t.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Select a " + what + " in the table first."); return -1; }
        return (int) t.getModel().getValueAt(t.convertRowIndexToModel(r), 0);
    }

    JComboBox<String> engineers() {
        JComboBox<String> c = new JComboBox<>();
        c.addItem("Unassigned");
        for (int i = 0; i < staff.getRowCount(); i++) if ("ENGINEER".equals(staff.getValueAt(i, 2))) c.addItem((String) staff.getValueAt(i, 1));
        return c;
    }

    void log(int ticket, String from, String to) {
        lg.addRow(new Object[]{ticket, from == null ? "-" : from, to, me.getSelectedItem(), now()});
    }

    // ---------- tickets ----------
    void newRequest() {
        JTextField name = new JTextField(), phone = new JTextField(), brand = new JTextField(), model = new JTextField(), problem = new JTextField();
        JComboBox<String> dev = new JComboBox<>(DEV), pri = new JComboBox<>(PRI), eng = engineers();
        pri.setSelectedItem("NORMAL");
        if (!ask("New repair request", new String[]{"Customer name", "Phone", "Device type", "Brand", "Model", "Problem", "Priority", "Engineer"},
                new JComponent[]{name, phone, dev, brand, model, problem, pri, eng})) return;
        if (name.getText().isBlank() || phone.getText().isBlank() || problem.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Customer name, phone and problem are required."); return;
        }
        int id = nextId(tk);
        tk.addRow(new Object[]{id, name.getText().trim(), phone.getText().trim(), dev.getSelectedItem() + " " + brand.getText().trim() + " " + model.getText().trim(),
                problem.getText().trim(), "RECEIVED", pri.getSelectedItem(), eng.getSelectedIndex() == 0 ? "Unassigned" : eng.getSelectedItem(), now()});
        log(id, null, "RECEIVED");
    }

    void changeStatus() {
        int id = selected(tkT, "ticket"); if (id < 0) return;
        int r = rowOf(tk, id);
        JComboBox<String> s = new JComboBox<>(ST);
        s.setSelectedItem(tk.getValueAt(r, 5));
        if (!ask("Change status of ticket #" + id, new String[]{"New status"}, new JComponent[]{s})) return;
        String old = (String) tk.getValueAt(r, 5), now = (String) s.getSelectedItem();
        if (old.equals(now)) return;
        tk.setValueAt(now, r, 5);
        log(id, old, now);
    }

    void addPart() {
        int id = selected(tkT, "ticket"); if (id < 0) return;
        if (rowOf(bill, id) >= 0 || billFor(id) >= 0) { JOptionPane.showMessageDialog(this, "This ticket is already invoiced."); return; }
        JComboBox<String> it = new JComboBox<>();
        for (int i = 0; i < inv.getRowCount(); i++) it.addItem(inv.getValueAt(i, 0) + " - " + inv.getValueAt(i, 2) + " (" + inv.getValueAt(i, 5) + " in stock)");
        JTextField qty = new JTextField("1");
        if (!ask("Add part to ticket #" + id, new String[]{"Item", "Quantity"}, new JComponent[]{it, qty})) return;
        int r = it.getSelectedIndex(), q = (int) num(qty.getText()), stock = (int) inv.getValueAt(r, 5);
        if (q < 1) { JOptionPane.showMessageDialog(this, "Enter a quantity of 1 or more."); return; }
        if (q > stock) { JOptionPane.showMessageDialog(this, "Only " + stock + " in stock."); return; }
        inv.setValueAt(stock - q, r, 5);
        items.add(new double[]{id, (int) inv.getValueAt(r, 0), q, Double.parseDouble((String) inv.getValueAt(r, 4))});
    }

    int billFor(int ticket) {
        for (int i = 0; i < bill.getRowCount(); i++) if ((int) bill.getValueAt(i, 1) == ticket) return i;
        return -1;
    }

    void makeInvoice() {
        int id = selected(tkT, "ticket"); if (id < 0) return;
        if (billFor(id) >= 0) { JOptionPane.showMessageDialog(this, "This ticket already has an invoice."); return; }
        JTextField labour = new JTextField("500"), disc = new JTextField("0");
        if (!ask("Create invoice for ticket #" + id, new String[]{"Labour charge", "Discount"}, new JComponent[]{labour, disc})) return;
        double parts = 0;
        for (double[] i : items) if ((int) i[0] == id) parts += i[2] * i[3];
        double l = num(labour.getText()), d = num(disc.getText()), tax = Math.round((parts + l - d) * 0.18 * 100) / 100.0;
        bill.addRow(new Object[]{nextId(bill), id, money(parts), money(l), money(d), money(tax), money(parts + l - d + tax), "UNPAID", "-", now()});
    }

    void history() {
        int id = selected(tkT, "ticket"); if (id < 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lg.getRowCount(); i++)
            if ((int) lg.getValueAt(i, 0) == id)
                sb.append(lg.getValueAt(i, 4)).append("   ").append(lg.getValueAt(i, 1)).append(" -> ").append(lg.getValueAt(i, 2))
                  .append("   by ").append(lg.getValueAt(i, 3)).append('\n');
        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(sb.toString(), 10, 45)), "History of ticket #" + id, JOptionPane.PLAIN_MESSAGE);
    }

    // ---------- inventory ----------
    void newItem() {
        JTextField sku = new JTextField(), name = new JTextField(), cat = new JTextField(), price = new JTextField(), qty = new JTextField("0"), re = new JTextField("3");
        if (!ask("Add inventory item", new String[]{"SKU", "Name", "Category", "Price", "Opening stock", "Reorder level"}, new JComponent[]{sku, name, cat, price, qty, re})) return;
        if (sku.getText().isBlank() || name.getText().isBlank()) { JOptionPane.showMessageDialog(this, "SKU and name are required."); return; }
        for (int i = 0; i < inv.getRowCount(); i++)
            if (inv.getValueAt(i, 1).equals(sku.getText().trim())) { JOptionPane.showMessageDialog(this, "That SKU already exists."); return; }
        inv.addRow(new Object[]{nextId(inv), sku.getText().trim(), name.getText().trim(), cat.getText().trim(), money(num(price.getText())), (int) num(qty.getText()), (int) num(re.getText())});
    }

    void restock() {
        int id = selected(invT, "item"); if (id < 0) return;
        String s = JOptionPane.showInputDialog(this, "How many units to add?", "5");
        int q = (int) num(s == null ? "0" : s), r = rowOf(inv, id);
        if (q > 0) inv.setValueAt((int) inv.getValueAt(r, 5) + q, r, 5);
    }

    // ---------- invoices and staff ----------
    void pay() {
        int id = selected(billT, "invoice"); if (id < 0) return;
        int r = rowOf(bill, id);
        if ("PAID".equals(bill.getValueAt(r, 7))) { JOptionPane.showMessageDialog(this, "This invoice is already paid."); return; }
        JComboBox<String> m = new JComboBox<>(PAY);
        if (!ask("Payment for invoice #" + id, new String[]{"Payment method"}, new JComponent[]{m})) return;
        bill.setValueAt("PAID", r, 7);
        bill.setValueAt(m.getSelectedItem(), r, 8);
    }

    void newStaff() {
        JTextField name = new JTextField(), spec = new JTextField(), phone = new JTextField(), email = new JTextField();
        JComboBox<String> des = new JComboBox<>(new String[]{"ADMIN", "RECEPTIONIST", "ENGINEER"});
        if (!ask("Add staff member", new String[]{"Name", "Designation", "Specialization", "Phone", "Email"}, new JComponent[]{name, des, spec, phone, email})) return;
        if (name.getText().isBlank()) { JOptionPane.showMessageDialog(this, "Name is required."); return; }
        staff.addRow(new Object[]{nextId(staff), name.getText().trim(), des.getSelectedItem(), spec.getText(), phone.getText(), email.getText()});
        me.addItem(name.getText().trim());
    }

    // ---------- sample data ----------
    void seed() {
        staff.addRow(new Object[]{1, "Abbas Raza", "ADMIN", "", "", ""});
        staff.addRow(new Object[]{2, "Nauman Hasan", "RECEPTIONIST", "", "", ""});
        staff.addRow(new Object[]{3, "Shloka Uniyal", "ENGINEER", "Laptops", "", ""});
        staff.addRow(new Object[]{4, "Tushar Batra", "ENGINEER", "Printers, desktops", "", ""});
        inv.addRow(new Object[]{1, "LCD-156", "15.6 inch laptop panel", "Display", "3200.00", 4, 3});
        inv.addRow(new Object[]{2, "SSD-512", "512GB SSD", "Storage", "3400.00", 2, 3});
        inv.addRow(new Object[]{3, "RAM-8D4", "8GB DDR4 RAM", "Memory", "1900.00", 9, 4});
        inv.addRow(new Object[]{4, "PRT-ROL", "Printer pickup roller", "Printer", "450.00", 6, 5});
        tk.addRow(new Object[]{1, "Anil Kumar", "9811122233", "LAPTOP Dell Inspiron 15", "Screen flickers and shuts down", "DIAGNOSING", "HIGH", "Shloka Uniyal", now()});
        lg.addRow(new Object[]{1, "-", "RECEIVED", "Nauman Hasan", now()});
        lg.addRow(new Object[]{1, "RECEIVED", "DIAGNOSING", "Shloka Uniyal", now()});
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            new ServiceCentreApp().setVisible(true);
        });
    }
}