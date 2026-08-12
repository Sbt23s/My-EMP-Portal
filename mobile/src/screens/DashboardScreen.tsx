import { View, Text, StyleSheet, ScrollView, ActivityIndicator, RefreshControl } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import dayjs from "dayjs";
import { api } from "@/api/client";
import { useAuth } from "@/context/AuthContext";
import { colors, spacing } from "@/theme";

export default function DashboardScreen() {
  const { user } = useAuth();
  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["dashboard", "me"],
    queryFn: async () => (await api.get("/dashboard/me")).data.data
  });

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  const stats = [
    { icon: "time-outline", label: "Today", value: data?.punchedInToday ? "In" : "Out", color: colors.primary },
    { icon: "calendar-outline", label: "Pending leave", value: data?.pendingLeaveRequests ?? 0, color: colors.accent },
    { icon: "cube-outline", label: "My assets", value: data?.myAssets ?? 0, color: colors.success },
    { icon: "help-buoy-outline", label: "Open tickets", value: data?.myOpenTickets ?? 0, color: colors.danger }
  ] as const;

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={{ padding: spacing.md }}
      refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
    >
      <Text style={styles.greeting}>Hello, {user?.name?.split(" ")[0]}</Text>
      <Text style={styles.date}>{dayjs().format("dddd, DD MMMM")}</Text>

      <View style={styles.grid}>
        {stats.map((s) => (
          <View key={s.label} style={styles.statCard}>
            <View style={[styles.iconWrap, { backgroundColor: s.color + "22" }]}>
              <Ionicons name={s.icon as any} size={20} color={s.color} />
            </View>
            <Text style={styles.statValue}>{s.value}</Text>
            <Text style={styles.statLabel}>{s.label}</Text>
          </View>
        ))}
      </View>

      <Text style={styles.section}>Leave balances</Text>
      {(data?.leaveBalances ?? []).length === 0 ? (
        <Text style={styles.empty}>No balances allocated.</Text>
      ) : (
        data.leaveBalances.map((b: any) => (
          <View key={b.leaveTypeId} style={styles.balanceRow}>
            <Text style={styles.balanceName}>{b.leaveTypeName}</Text>
            <Text style={styles.balanceValue}>
              {b.available} / {b.allocated}
            </Text>
          </View>
        ))
      )}

      <Text style={styles.section}>Recent activity</Text>
      {(data?.recentNotifications ?? []).length === 0 ? (
        <Text style={styles.empty}>Nothing new.</Text>
      ) : (
        data.recentNotifications.map((n: any) => (
          <View key={n.id} style={styles.notifRow}>
            <View style={styles.dot} />
            <View style={{ flex: 1 }}>
              <Text style={styles.notifTitle}>{n.title}</Text>
              <Text style={styles.notifTime}>{dayjs(n.createdAt).format("DD MMM, h:mm A")}</Text>
            </View>
          </View>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.bg },
  greeting: { fontSize: 24, fontWeight: "800", color: colors.ink },
  date: { fontSize: 14, color: colors.muted, marginBottom: spacing.md },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  statCard: {
    width: "48%", backgroundColor: colors.card, borderRadius: 12, padding: spacing.md,
    borderWidth: 1, borderColor: colors.border
  },
  iconWrap: {
    width: 40, height: 40, borderRadius: 10, alignItems: "center", justifyContent: "center", marginBottom: spacing.sm
  },
  statValue: { fontSize: 24, fontWeight: "800", color: colors.ink },
  statLabel: { fontSize: 12, color: colors.muted },
  section: { fontSize: 13, fontWeight: "700", color: colors.muted, marginTop: spacing.lg, marginBottom: spacing.sm, textTransform: "uppercase" },
  balanceRow: {
    flexDirection: "row", justifyContent: "space-between", backgroundColor: colors.card,
    padding: spacing.md, borderRadius: 10, borderWidth: 1, borderColor: colors.border, marginBottom: spacing.sm
  },
  balanceName: { fontSize: 14, color: colors.ink },
  balanceValue: { fontSize: 14, fontWeight: "700", color: colors.primary },
  empty: { fontSize: 13, color: colors.muted, paddingVertical: spacing.sm },
  notifRow: { flexDirection: "row", gap: spacing.sm, marginBottom: spacing.md, alignItems: "flex-start" },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.primary, marginTop: 6 },
  notifTitle: { fontSize: 14, color: colors.ink, fontWeight: "500" },
  notifTime: { fontSize: 12, color: colors.muted }
});
