import { useState } from "react";
import {
  View, Text, StyleSheet, TouchableOpacity, ActivityIndicator, Alert, ScrollView
} from "react-native";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { Ionicons } from "@expo/vector-icons";
import dayjs from "dayjs";
import { api, apiMessage } from "@/api/client";
import { colors, spacing } from "@/theme";

const MODES = ["OFFICE", "WFH", "SITE"] as const;

export default function AttendanceScreen() {
  const qc = useQueryClient();
  const [mode, setMode] = useState<(typeof MODES)[number]>("OFFICE");
  const [locating, setLocating] = useState(false);

  const today = useQuery({
    queryKey: ["attendance", "today"],
    queryFn: async () => (await api.get("/attendance/today")).data.data
  });

  const punch = useMutation({
    mutationFn: async (kind: "punch-in" | "punch-out") => {
      let latitude: number | undefined;
      let longitude: number | undefined;
      if (mode !== "WFH") {
        setLocating(true);
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") {
          setLocating(false);
          throw new Error("Location permission is needed for office/site punches.");
        }
        const pos = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.High
        });
        latitude = pos.coords.latitude;
        longitude = pos.coords.longitude;
        setLocating(false);
      }
      return (await api.post(`/attendance/${kind}`, { latitude, longitude, mode })).data;
    },
    onSuccess: (res) => {
      Alert.alert("Done", res.message || "Recorded");
      qc.invalidateQueries({ queryKey: ["attendance"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (e) => {
      setLocating(false);
      Alert.alert("Could not record", apiMessage(e));
    }
  });

  const t = today.data;
  const punchedIn = !!t?.punchInAt;
  const punchedOut = !!t?.punchOutAt;
  const busy = punch.isPending || locating;

  return (
    <ScrollView style={styles.root} contentContainerStyle={{ padding: spacing.md }}>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{dayjs().format("dddd, DD MMM")}</Text>

        <View style={styles.times}>
          <View style={styles.timeBox}>
            <Text style={styles.timeLabel}>Punch in</Text>
            <Text style={styles.timeValue}>
              {t?.punchInAt ? dayjs(t.punchInAt).format("h:mm A") : "—"}
            </Text>
          </View>
          <View style={styles.timeBox}>
            <Text style={styles.timeLabel}>Punch out</Text>
            <Text style={styles.timeValue}>
              {t?.punchOutAt ? dayjs(t.punchOutAt).format("h:mm A") : "—"}
            </Text>
          </View>
        </View>

        <Text style={styles.modeLabel}>Mode</Text>
        <View style={styles.modeRow}>
          {MODES.map((m) => (
            <TouchableOpacity
              key={m}
              style={[styles.modeChip, mode === m && styles.modeChipActive]}
              onPress={() => setMode(m)}
              disabled={punchedOut}
            >
              <Text style={[styles.modeText, mode === m && styles.modeTextActive]}>{m}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {mode !== "WFH" && (
          <View style={styles.hint}>
            <Ionicons name="location-outline" size={14} color={colors.muted} />
            <Text style={styles.hintText}>GPS location captured on punch.</Text>
          </View>
        )}

        {!punchedIn ? (
          <TouchableOpacity style={styles.btn} onPress={() => punch.mutate("punch-in")} disabled={busy}>
            {busy ? <ActivityIndicator color="#fff" /> : (
              <>
                <Ionicons name="log-in-outline" size={18} color="#fff" />
                <Text style={styles.btnText}>Punch in</Text>
              </>
            )}
          </TouchableOpacity>
        ) : !punchedOut ? (
          <TouchableOpacity
            style={[styles.btn, { backgroundColor: colors.accent }]}
            onPress={() => punch.mutate("punch-out")}
            disabled={busy}
          >
            {busy ? <ActivityIndicator color="#fff" /> : (
              <>
                <Ionicons name="log-out-outline" size={18} color="#fff" />
                <Text style={styles.btnText}>Punch out</Text>
              </>
            )}
          </TouchableOpacity>
        ) : (
          <View style={styles.done}>
            <Text style={styles.doneText}>Day complete — see you tomorrow.</Text>
          </View>
        )}

        {t && (
          <View style={styles.badges}>
            <Text style={styles.badge}>{t.status}</Text>
            {t.late && <Text style={[styles.badge, styles.badgeDanger]}>Late</Text>}
            {t.withinGeofence === false && (
              <Text style={[styles.badge, styles.badgeWarn]}>Outside geofence</Text>
            )}
          </View>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  card: { backgroundColor: colors.card, borderRadius: 14, padding: spacing.lg, borderWidth: 1, borderColor: colors.border },
  cardTitle: { fontSize: 16, fontWeight: "700", color: colors.ink, marginBottom: spacing.md },
  times: { flexDirection: "row", gap: spacing.sm },
  timeBox: { flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: spacing.md },
  timeLabel: { fontSize: 12, color: colors.muted },
  timeValue: { fontSize: 20, fontWeight: "800", color: colors.ink },
  modeLabel: { fontSize: 13, fontWeight: "600", color: colors.ink, marginTop: spacing.lg, marginBottom: spacing.sm },
  modeRow: { flexDirection: "row", gap: spacing.sm },
  modeChip: { flex: 1, paddingVertical: 10, borderRadius: 10, borderWidth: 1, borderColor: colors.border, alignItems: "center" },
  modeChipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  modeText: { fontSize: 13, color: colors.muted, fontWeight: "600" },
  modeTextActive: { color: "#fff" },
  hint: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: spacing.sm },
  hintText: { fontSize: 12, color: colors.muted },
  btn: {
    flexDirection: "row", gap: spacing.sm, height: 52, backgroundColor: colors.primary, borderRadius: 12,
    alignItems: "center", justifyContent: "center", marginTop: spacing.lg
  },
  btnText: { color: "#fff", fontWeight: "700", fontSize: 16 },
  done: { marginTop: spacing.lg, padding: spacing.md, borderRadius: 10, backgroundColor: colors.success + "22" },
  doneText: { color: colors.success, textAlign: "center", fontWeight: "600" },
  badges: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.md, flexWrap: "wrap" },
  badge: { fontSize: 12, fontWeight: "600", color: colors.primary, backgroundColor: colors.primarySoft, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  badgeDanger: { color: colors.danger, backgroundColor: colors.danger + "22" },
  badgeWarn: { color: "#92400e", backgroundColor: colors.accent + "33" }
});
