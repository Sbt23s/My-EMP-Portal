import { useState } from "react";
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, KeyboardAvoidingView, Platform
} from "react-native";
import { useAuth } from "@/context/AuthContext";
import { apiMessage } from "@/api/client";
import { colors, spacing } from "@/theme";

export default function LoginScreen() {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!username.trim()) {
      Alert.alert("Check username", "Please enter your username.");
      return;
    }
    setBusy(true);
    try {
      await login(username.trim(), password);
    } catch (e) {
      Alert.alert("Login failed", apiMessage(e, "Check your username and password."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <View style={styles.brandBadge}>
        <Text style={styles.brandBadgeText}>HR</Text>
      </View>
      <Text style={styles.title}>Pixous HR</Text>
      <Text style={styles.subtitle}>Sign in with your username</Text>

      <Text style={styles.label}>Username</Text>
      <TextInput
        style={styles.input}
        value={username}
        onChangeText={setUsername}
        placeholder="your username"
        autoCapitalize="none"
        autoCorrect={false}
        placeholderTextColor={colors.muted}
      />

      <Text style={styles.label}>Password</Text>
      <TextInput
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        placeholder="••••••••"
        secureTextEntry
        placeholderTextColor={colors.muted}
      />

      <TouchableOpacity style={styles.button} onPress={submit} disabled={busy}>
        {busy ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Sign in</Text>}
      </TouchableOpacity>

      <View style={styles.demo}>
        <Text style={styles.demoTitle}>Demo · password Test1234@</Text>
        <Text style={styles.demoText}>Employee arun · Manager karthik</Text>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, padding: spacing.lg, justifyContent: "center" },
  brandBadge: {
    width: 56, height: 56, borderRadius: 14, backgroundColor: colors.primary,
    alignItems: "center", justifyContent: "center", marginBottom: spacing.md
  },
  brandBadgeText: { color: "#fff", fontWeight: "800", fontSize: 20 },
  title: { fontSize: 28, fontWeight: "800", color: colors.ink },
  subtitle: { fontSize: 14, color: colors.muted, marginTop: 4, marginBottom: spacing.lg },
  label: { fontSize: 13, fontWeight: "600", color: colors.ink, marginBottom: 6, marginTop: spacing.md },
  input: {
    height: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10,
    paddingHorizontal: spacing.md, backgroundColor: colors.card, color: colors.ink, fontSize: 15
  },
  button: {
    height: 50, backgroundColor: colors.primary, borderRadius: 10, alignItems: "center",
    justifyContent: "center", marginTop: spacing.lg
  },
  buttonText: { color: "#fff", fontWeight: "700", fontSize: 16 },
  demo: {
    marginTop: spacing.xl, padding: spacing.md, borderRadius: 10,
    borderWidth: 1, borderColor: colors.border, borderStyle: "dashed"
  },
  demoTitle: { fontSize: 12, fontWeight: "600", color: colors.muted },
  demoText: { fontSize: 12, color: colors.muted, marginTop: 4 }
});
