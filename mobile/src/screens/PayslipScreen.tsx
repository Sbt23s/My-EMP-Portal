import { View, Text, StyleSheet, ScrollView, ActivityIndicator } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { api } from "@/api/client";
import { colors, spacing } from "@/theme";

const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

function money(v: number) {
  return "₹" + Number(v).toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

export default function PayslipScreen() {
  const { data, isLoading } = useQuery({
    queryKey: ["payslips"],
    queryFn: async () => (await api.get("/payroll/payslip/list")).data.data
  });

  return (
    <ScrollView style={styles.root} contentContainerStyle={{ padding: spacing.md }}>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: spacing.xl }} />
      ) : (data ?? []).length === 0 ? (
        <View style={styles.empty}>
          <Ionicons name="wallet-outline" size={32} color={colors.muted} />
          <Text style={styles.emptyText}>No payslips yet.</Text>
        </View>
      ) : (
        (data ?? []).map((p: any) => (
          <View key={p.id} style={styles.card}>
            <View style={{ flex: 1 }}>
              <Text style={styles.month}>
                {MONTHS[p.payMonth - 1]} {p.payYear}
              </Text>
              <Text style={styles.gross}>Gross {money(p.grossSalary)}</Text>
            </View>
            <View style={{ alignItems: "flex-end" }}>
              <Text style={styles.net}>{money(p.netPay)}</Text>
              <Text style={styles.netLabel}>Net pay</Text>
            </View>
          </View>
        ))
      )}
      <Text style={styles.footnote}>
        Open the web portal to download PDF payslips.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  card: { flexDirection: "row", alignItems: "center", backgroundColor: colors.card, borderRadius: 12, padding: spacing.md, borderWidth: 1, borderColor: colors.border, marginBottom: spacing.sm },
  month: { fontSize: 16, fontWeight: "700", color: colors.ink },
  gross: { fontSize: 13, color: colors.muted, marginTop: 2 },
  net: { fontSize: 18, fontWeight: "800", color: colors.primary },
  netLabel: { fontSize: 11, color: colors.muted },
  empty: { alignItems: "center", marginTop: spacing.xl, gap: spacing.sm },
  emptyText: { color: colors.muted },
  footnote: { fontSize: 12, color: colors.muted, textAlign: "center", marginTop: spacing.md }
});
