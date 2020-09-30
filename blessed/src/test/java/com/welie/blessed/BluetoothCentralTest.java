package com.welie.blessed;

import com.welie.blessed.bluez.BluezAdapter;
import com.welie.blessed.bluez.BluezDevice;
import org.bluez.exceptions.BluezFailedException;
import org.bluez.exceptions.BluezInvalidArgumentsException;
import org.bluez.exceptions.BluezNotReadyException;
import org.bluez.exceptions.BluezNotSupportedException;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static com.welie.blessed.BluetoothCentral.*;
import static com.welie.blessed.BluetoothPeripheral.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BluetoothCentralTest {
    BluetoothCentral central;

    @Mock
    BluetoothCentralCallback callback;

    @Mock
    BluezAdapter bluezAdapter;

    @Mock
    BluezDevice bluezDevice;

    private static final UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
    private static final String DUMMY_MAC_ADDRESS = "12:34:56:65:43:21";
    private static final String DUMMY_MAC_ADDRESS_PATH = DUMMY_MAC_ADDRESS.replace(":", "_");
    private static final String DUMMY_PERIPHERAL_NAME = "Polar H7";

    @BeforeEach
    void setup() {
    }

    @Test
    void constructorNotNullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            central = new BluetoothCentral(null, null, null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            central = new BluetoothCentral(callback, null, null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            central = new BluetoothCentral(callback, Collections.emptySet(), null);
        });
    }

    @Test
    void ifAdapterIsOffThenTurnItOnTest() throws InterruptedException {
        // Given
        when(bluezAdapter.isPowered()).thenReturn(false);

        // When
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        Thread.sleep(10);

        // Then
        verify(bluezAdapter).setPowered(true);
    }

    @Test
    void ifScanForPeripheralsIsCalledThenTheAnUnfilteredScanIsStarted() throws InterruptedException, BluezFailedException, BluezNotReadyException, BluezNotSupportedException, BluezInvalidArgumentsException {
        // Given
        when(bluezAdapter.isDiscovering()).thenReturn(false);
        when(bluezAdapter.isPowered()).thenReturn(true);
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        // When
        central.scanForPeripherals();

        // scanForPeripherals is async so wait a little bit
        Thread.sleep(100);

        // Then : Verify scan filters
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(bluezAdapter).setDiscoveryFilter(captor.capture());

        Map<String, Variant<?>> filterMap = captor.getValue();
        assertEquals(3, filterMap.size());

        String transport = (String) filterMap.get("Transport").getValue();
        assertEquals("le", transport );

        Boolean duplicateData = (Boolean) filterMap.get("DuplicateData").getValue();
        assertTrue(duplicateData);

        Short rssi = (Short) filterMap.get("RSSI").getValue();
        assertEquals(DISCOVERY_RSSI_THRESHOLD, (int) rssi);

        // Then : Verify that no name, mac addresses or service UUID filters are set
        assertEquals(0, central.scanServiceUUIDs.length);
        assertEquals(0, central.scanPeripheralAddresses.length);
        assertEquals(0, central.scanPeripheralNames.length);

        // Then : Verify that scan is really started
        verify(bluezAdapter).startDiscovery();
    }

    @Test
    void ifScanForPeripheralsWithServicesIsCalledThenTheAFilteredScanIsStarted() throws InterruptedException, DBusException {
        // Given
        when(bluezAdapter.isDiscovering()).thenReturn(false);
        when(bluezAdapter.isPowered()).thenReturn(true);
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        // When
        central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID});

        // scanForPeripheralsWithServices is async so wait a little bit
        Thread.sleep(100);

        // Then : Verify scan filters
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(bluezAdapter).setDiscoveryFilter(captor.capture());

        Map<String, Variant<?>> filterMap = captor.getValue();
        assertEquals(3, filterMap.size());

        String transport = (String) filterMap.get("Transport").getValue();
        assertEquals("le", transport );

        Boolean duplicateData = (Boolean) filterMap.get("DuplicateData").getValue();
        assertTrue(duplicateData);

        Short rssi = (Short) filterMap.get("RSSI").getValue();
        assertEquals(DISCOVERY_RSSI_THRESHOLD, (int) rssi);

        // Then : Verify that no name, mac addresses or service UUID filters are set
        assertEquals(1, central.scanServiceUUIDs.length);
        assertEquals(BLP_SERVICE_UUID, central.scanServiceUUIDs[0]);
        assertEquals(0, central.scanPeripheralAddresses.length);
        assertEquals(0, central.scanPeripheralNames.length);

        // Then : Verify that startDiscovery is really called
        verify(bluezAdapter).startDiscovery();

        // Wait for properties changed to get confirmation the scan is started
        Properties.PropertiesChanged propertiesChangedSignal = getPropertiesChangeSignalDiscoveryStarted();
        central.handleSignal(propertiesChangedSignal);
        Thread.sleep(100);
        assertTrue(central.isScanning);
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangeSignalDiscoveryStarted() throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_DISCOVERING, new Variant<>(true));
        return new Properties.PropertiesChanged("/org/bluez/hci0", BLUEZ_ADAPTER_INTERFACE, propertiesChanged,new ArrayList() );
    }

    @Test
    void WhenScanningAndAnInterfaceIsAddedThenOnDiscoveredPeripheralIsCalled() throws InterruptedException, DBusException {
        // Given
        startUnfilteredScan();

        // When
        ObjectManager.InterfacesAdded interfacesAdded = getInterfacesAddedNewDevice();
        central.handleInterfaceAddedForDevice(interfacesAdded.getPath(), interfacesAdded.getInterfaces().get(BLUEZ_DEVICE_INTERFACE));
        Thread.sleep(100);

        // Then
        ArgumentCaptor<BluetoothPeripheral> peripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(callback).onDiscoveredPeripheral(peripheralCaptor.capture(), scanResultCaptor.capture());

        // Then : check if the peripheral and scanResult have the right values
        BluetoothPeripheral peripheral = peripheralCaptor.getValue();
        ScanResult scanResult = scanResultCaptor.getValue();
        assertEquals(DUMMY_MAC_ADDRESS, peripheral.getAddress());
        assertEquals(DUMMY_PERIPHERAL_NAME, scanResult.getName());
        assertEquals(DUMMY_MAC_ADDRESS, scanResult.getAddress());
    }

    @NotNull
    private ObjectManager.InterfacesAdded getInterfacesAddedNewDevice() throws DBusException {
        String objectPath = "/";
        DBusPath dBusPath = new DBusPath("/org/bluez/hci0/dev_00_11_22_33_44_55");
        HashMap<String, Map<String, Variant<?>>> interfaceAddedMap = new HashMap<>();
        Map<String, Variant<?>> interfaceMap = new HashMap<>();
        interfaceMap.put(PROPERTY_ADDRESS, new Variant<>(DUMMY_MAC_ADDRESS));
        interfaceMap.put(PROPERTY_NAME, new Variant<>(DUMMY_PERIPHERAL_NAME));
        interfaceMap.put(PROPERTY_RSSI, new Variant<>(new Short("-50")));
        ArrayList<String> uuids = new ArrayList<>();
        uuids.add(BLP_SERVICE_UUID.toString());
        interfaceMap.put(PROPERTY_SERVICE_UUIDS, new Variant<>(uuids, String.class));
        interfaceAddedMap.put(BLUEZ_DEVICE_INTERFACE, interfaceMap);
        ObjectManager.InterfacesAdded interfacesAdded = new ObjectManager.InterfacesAdded(objectPath,dBusPath, interfaceAddedMap);
        return interfacesAdded;
    }

    private void startUnfilteredScan() throws InterruptedException, DBusException {
        when(bluezAdapter.isDiscovering()).thenReturn(false);
        when(bluezAdapter.isPowered()).thenReturn(true);
        when(bluezAdapter.getPath(DUMMY_MAC_ADDRESS)).thenReturn(DUMMY_MAC_ADDRESS_PATH);
        when(bluezAdapter.getBluezDeviceByPath(DUMMY_MAC_ADDRESS_PATH)).thenReturn(bluezDevice);
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);
        central.scanForPeripherals();
        Thread.sleep(100);
        Properties.PropertiesChanged propertiesChangedSignal = getPropertiesChangeSignalDiscoveryStarted();
        central.handleSignal(propertiesChangedSignal);
    }

    @Test
    void ifScanForPeripheralsWithServicesIsCalledWithBadArgumentsThenExceptionsAreThrown() {
        // Given
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        assertThrows(NullPointerException.class, ()-> {
                central.scanForPeripheralsWithServices(null);
        });
        assertThrows(IllegalArgumentException.class, ()-> {
            central.scanForPeripheralsWithServices(new UUID[0]);
        });
    }

    @Test
    void ifScanForPeripheralsWithAddressesIsCalledThenTheAFilteredScanIsStarted() throws InterruptedException, BluezFailedException, BluezNotReadyException, BluezNotSupportedException, BluezInvalidArgumentsException {
        // Given
        when(bluezAdapter.isDiscovering()).thenReturn(false);
        when(bluezAdapter.isPowered()).thenReturn(true);
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        // When
        central.scanForPeripheralsWithAddresses(new String[]{DUMMY_MAC_ADDRESS});

        // scanForPeripheralsWithServices is async so wait a little bit
        Thread.sleep(100);

        // Then : Verify scan filters
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(bluezAdapter).setDiscoveryFilter(captor.capture());

        Map<String, Variant<?>> filterMap = captor.getValue();
        assertEquals(3, filterMap.size());

        String transport = (String) filterMap.get("Transport").getValue();
        assertEquals("le", transport );

        Boolean duplicateData = (Boolean) filterMap.get("DuplicateData").getValue();
        assertTrue(duplicateData);

        Short rssi = (Short) filterMap.get("RSSI").getValue();
        assertEquals(DISCOVERY_RSSI_THRESHOLD, (int) rssi);

        // Then : Verify that no name, mac addresses or service UUID filters are set
        assertEquals(0, central.scanServiceUUIDs.length);
        assertEquals(1, central.scanPeripheralAddresses.length);
        assertEquals(DUMMY_MAC_ADDRESS, central.scanPeripheralAddresses[0]);
        assertEquals(0, central.scanPeripheralNames.length);

        // Then : Verify that scan is really started
        verify(bluezAdapter).startDiscovery();
    }

    @Test
    void ifScanForPeripheralsWithAddressesIsCalledWithBadArgumentsThenExceptionsAreThrown() {
        // Given
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        assertThrows(NullPointerException.class, ()-> {
            central.scanForPeripheralsWithAddresses(null);
        });
        assertThrows(IllegalArgumentException.class, ()-> {
            central.scanForPeripheralsWithAddresses(new String[0]);
        });
    }

    @Test
    void ifScanForPeripheralsWithNamesIsCalledThenTheAFilteredScanIsStarted() throws InterruptedException, BluezFailedException, BluezNotReadyException, BluezNotSupportedException, BluezInvalidArgumentsException {
        // Given
        when(bluezAdapter.isDiscovering()).thenReturn(false);
        when(bluezAdapter.isPowered()).thenReturn(true);
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        // When
        central.scanForPeripheralsWithNames(new String[]{DUMMY_PERIPHERAL_NAME});

        // scanForPeripheralsWithServices is async so wait a little bit
        Thread.sleep(100);

        // Then : Verify scan filters
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(bluezAdapter).setDiscoveryFilter(captor.capture());

        Map<String, Variant<?>> filterMap = captor.getValue();
        assertEquals(3, filterMap.size());

        String transport = (String) filterMap.get("Transport").getValue();
        assertEquals("le", transport );

        Boolean duplicateData = (Boolean) filterMap.get("DuplicateData").getValue();
        assertTrue(duplicateData);

        Short rssi = (Short) filterMap.get("RSSI").getValue();
        assertEquals(DISCOVERY_RSSI_THRESHOLD, (int) rssi);

        // Then : Verify that no name, mac addresses or service UUID filters are set
        assertEquals(0, central.scanServiceUUIDs.length);
        assertEquals(0, central.scanPeripheralAddresses.length);
        assertEquals(1, central.scanPeripheralNames.length);
        assertEquals(DUMMY_PERIPHERAL_NAME, central.scanPeripheralNames[0]);

        // Then : Verify that scan is really started
        verify(bluezAdapter).startDiscovery();
    }

    @Test
    void ifScanForPeripheralsWithNamesIsCalledWithBadArgumentsThenExceptionsAreThrown() {
        // Given
        central = new BluetoothCentral(callback, Collections.emptySet(), bluezAdapter);

        assertThrows(NullPointerException.class, ()-> {
            central.scanForPeripheralsWithNames(null);
        });
        assertThrows(IllegalArgumentException.class, ()-> {
            central.scanForPeripheralsWithNames(new String[0]);
        });
    }
}
