import Foundation
import Security
import SharedLogic

/// Atomic, device-only session storage. No passwords, OTPs or iCloud-synchronized items are stored.
final class KeychainAuthStorage: AuthSessionStorage {
    private let lock = NSLock()
    private let service = "com.ndynagn.kmp.news.auth"

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "session",
            kSecAttrSynchronizable as String: false,
        ]
    }

    func read() -> AuthStorageRead {
        lock.lock()
        defer { lock.unlock() }
        var attributes = query
        attributes[kSecReturnData as String] = true
        attributes[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(attributes as CFDictionary, &result)
        if status == errSecItemNotFound { return AuthStorageRead(value: nil, failed: false) }
        guard status == errSecSuccess, let data = result as? Data,
            let value = String(data: data, encoding: .utf8)
        else {
            return AuthStorageRead(value: nil, failed: true)
        }
        return AuthStorageRead(value: value, failed: false)
    }

    func write(value: String?) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard let value else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        let changes: [String: Any] = [
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        let status = SecItemUpdate(query as CFDictionary, changes as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        return SecItemAdd(query.merging(changes) { _, new in new } as CFDictionary, nil) == errSecSuccess
    }
}
