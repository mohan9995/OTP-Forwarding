import React from 'react';
import { StyleSheet, Text, View, SafeAreaView, StatusBar } from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#0D47A1" />
      <View style={styles.content}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>100% Offline & Zero Network</Text>
        </View>

        <Text style={styles.title}>OTP SMS Forwarder</Text>
        <Text style={styles.subtitle}>
          Native Kotlin BroadcastReceiver & Filter Engine Active
        </Text>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Background Service</Text>
          <Text style={styles.cardBody}>
            Listening for incoming OTP SMS messages. Filtered OTPs are automatically forwarded to your designated recipient number.
          </Text>
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            • Android 13+ Restricted Settings enabled
          </Text>
          <Text style={styles.infoText}>
            • Battery Optimization exemption configured
          </Text>
          <Text style={styles.infoText}>
            • Zero internet permissions required
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  content: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  badge: {
    backgroundColor: '#1E293B',
    borderColor: '#38BDF8',
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    marginBottom: 16,
  },
  badgeText: {
    color: '#38BDF8',
    fontSize: 12,
    fontWeight: '600',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FFFFFF',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    marginTop: 8,
    marginBottom: 32,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 20,
    width: '100%',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  cardBody: {
    fontSize: 14,
    color: '#94A3B8',
    lineHeight: 20,
  },
  infoBox: {
    width: '100%',
    padding: 16,
    backgroundColor: 'rgba(30, 41, 59, 0.5)',
    borderRadius: 12,
  },
  infoText: {
    color: '#64748B',
    fontSize: 12,
    marginBottom: 4,
  },
});
