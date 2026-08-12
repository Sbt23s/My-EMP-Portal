import { View, Text, StyleSheet, ScrollView, ActivityIndicator, RefreshControl } from "react-native";
import { useQuery } from "@tanstack/react-query";
import dayjs from "dayjs";
import { api } from "@/api/client";
import { colors, spacing } from "@/theme";

function badgeColor(status: string) {
  switch (status) {
    case "APPROVED": return colors.success;
    case "REJECTED": case "CANCELLED": return colors.danger;
    default: return colors.accent;
  }
}

export default function LeaveScreen() {
  const balances = useQuery({
    queryKey: ["leave", "balances"],
    queryFn: async () => (await api.get("/leave/balances")).data.data
  });
  const requests = useQuery({
    queryKey: ["leave", "me"],
    queryFn: async () => (await api.get("/leave/me?size=50")).data.content
  });

  const loading = balances.isLoading || requests.isLoading;

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={{ padding: spacing.md }}
      refreshControl={
        <RefreshControl
          refreshing={balances.isRefetching}
          onRefresh={() => {
            balances.refetch();
            requests.refetch();
          }}
        />
      }
    >
      {loading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: spacing.xl }} />
      ) : (
        <>
          <Text style={styles.section}>Balances</Text>
          <View style={styles.balGrid}>
            {(balances.data ?? []).map((b: any) => (
              <View key={b.leaveTypeId} style={styles.balCard}>
                <Text style={styles.balValue}>{b.available}</Text>
                <Text style={styles.balName}>{b.leaveTypeCode}</Text>
                <Text style={styles.balSub}>of {b.allocated}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.section}>My requests</Text>
          {(requests.data ?? []).length === 0 ? (
            <Text style={styles.empty}>No leave requests yet.</Text>
          ) : (
            (requests.data ?? []).map((r: any) => (
              <View key={r.id} style={styles.reqCard}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.reqType}>{r.leaveTypeName}</Text>
                  <Text style={styles.reqDates}>
                    {dayjs(r.fromDate).format("DD MMM")} – {dayjs(r.toDate).format("DD MMM")} · {r.workingDays}d
                  </Text>
                </View>
                <Text style={[styles.reqStatus, { color: badgeColor(r.status), backgroundColor: badgeColor(r.status) + "22" }]}>
                  {r.status}
                </Text>
              </View>
            ))
          )}
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  section: { fontSize: 13, fontWeight: "700", color: colors.muted, marginTop: spacing.md, marginBottom: spacing.sm, textTransform: "uppercase" },
  balGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  balCard: { width: "31%", backgroundColor: colors.card, borderRadius: 12, padding: spacing.md, borderWidth: 1, borderColor: colors.border, alignItems: "center" },
  balValue: { fontSize: 22, fontWeight: "800", color: colors.primary },
  balName: { fontSize: 12, fontWeight: "600", color: colors.ink },
  balSub: { fontSize: 11, color: colors.muted },
  reqCard: { flexDirection: "row", alignItems: "center", backgroundColor: colors.card, borderRadius: 10, padding: spacing.md, borderWidth: 1, borderColor: colors.border, marginBottom: spacing.sm },
  reqType: { fontSize: 14, fontWeight: "600", color: colors.ink },
  reqDates: { fontSize: 12, color: colors.muted, marginTop: 2 },
  reqStatus: { fontSize: 11, fontWeight: "700", paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999, overflow: "hidden" },
  empty: { fontSize: 13, color: colors.muted }
});
