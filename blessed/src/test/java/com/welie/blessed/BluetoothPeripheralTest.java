package com.welie.blessed;

import com.welie.blessed.bluez.BluezDevice;
import com.welie.blessed.bluez.BluezGattCharacteristic;
import com.welie.blessed.bluez.BluezGattDescriptor;
import com.welie.blessed.bluez.BluezGattService;
import com.welie.blessed.internal.Handler;
import com.welie.blessed.internal.InternalCallback;
import org.bluez.exceptions.*;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static com.welie.blessed.BluetoothGattCharacteristic.*;
import static com.welie.blessed.BluetoothPeripheral.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BluetoothPeripheralTest {

    @Mock
    DBusConnection dBusConnection;

    @Mock
    BluezDevice bluezDevice;

    @Mock
    BluetoothCentral central;

    @Mock
    InternalCallback internalCallback;

    @Mock
    BluetoothPeripheralCallback peripheralCallback;

    Handler callbackHandler = new Handler("BluetoothPeripheralTest-callback");

    private static final String DUMMY_MAC_ADDRESS_BLP = "12:34:56:65:43:21";
    private static final String DUMMY_PERIPHERAL_NAME_BLP = "Beurer BM57";
    private static final UUID BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
    private static final UUID HTS_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    private static final UUID BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
    private static final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    @Test
    void When_creating_a_peripheral_with_null_for_the_central_parameter_then_a_NPE_is_thrown() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            BluetoothPeripheral peripheral = new BluetoothPeripheral(null, bluezDevice, DUMMY_PERIPHERAL_NAME_BLP, DUMMY_MAC_ADDRESS_BLP, internalCallback, peripheralCallback, callbackHandler);
        });
    }

    @Test
    void When_creating_a_peripheral_with_null_for_the_deviceAddress_parameter_then_a_NPE_is_thrown() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            BluetoothPeripheral peripheral = new BluetoothPeripheral(central, bluezDevice, DUMMY_PERIPHERAL_NAME_BLP, null, internalCallback, peripheralCallback, callbackHandler);
        });
    }

    @Test
    void When_creating_a_peripheral_with_null_for_the_internalCallback_parameter_then_a_NPE_is_thrown() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            BluetoothPeripheral peripheral = new BluetoothPeripheral(central, bluezDevice, DUMMY_PERIPHERAL_NAME_BLP, DUMMY_MAC_ADDRESS_BLP, null, peripheralCallback, callbackHandler);
        });
    }

    @Test
    void When_creating_a_peripheral_with_null_for_the_handler_parameter_then_a_NPE_is_thrown() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            BluetoothPeripheral peripheral = new BluetoothPeripheral(central, bluezDevice, DUMMY_PERIPHERAL_NAME_BLP, DUMMY_MAC_ADDRESS_BLP, internalCallback, peripheralCallback, null);
        });
    }

    @Test
    void Given_a_peripheral_when_connect_is_called_then_a_connection_is_attempted() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        // When
        peripheral.connect();

        // Then
        verify(bluezDevice).connect();
    }

    @Test
    void Given_a_connected_peripheral_when_connect_is_called_then_connected_is_called() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        doThrow(new BluezAlreadyConnectedException("already connected"))
                .when(bluezDevice)
                .connect();

        // When
        peripheral.connect();

        // Then
        verify(internalCallback).connected(peripheral);
    }

    @Test
    void Given_a_disconnected_peripheral_when_connect_is_called_and_bluez_not_ready_then_connectionFailed_is_called() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        doThrow(new BluezNotReadyException("not ready"))
                .when(bluezDevice)
                .connect();

        // When
        peripheral.connect();

        // Then
        verify(internalCallback).connectFailed(peripheral);
        assertEquals(STATE_DISCONNECTED, peripheral.getState());
    }

    // TODO, consider simply doing nothing when this happens....
    @Test
    void Given_a_disconnected_peripheral_when_connect_is_called_and_connectionInProgress_then_connectionFailed_is_called() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        doThrow(new BluezInProgressException("connection in progress"))
                .when(bluezDevice)
                .connect();

        // When
        peripheral.connect();

        // Then
        verify(internalCallback).connectFailed(peripheral);
        assertEquals(STATE_DISCONNECTED, peripheral.getState());
    }

    @Test
    void Given_a_disconnected_peripheral_when_connect_is_called_and_connectionFailed_then_connectionFailed_is_called() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        doThrow(new BluezFailedException("connect failed"))
                .when(bluezDevice)
                .connect();

        // When
        peripheral.connect();

        // Then
        verify(internalCallback).connectFailed(peripheral);
        assertEquals(STATE_DISCONNECTED, peripheral.getState());
    }

    @Test
    void Given_a_disconnected_peripheral_when_connect_is_called_and_dbusException_then_connectionFailed_is_called() throws BluezFailedException, BluezAlreadyConnectedException, BluezNotReadyException, BluezInProgressException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        doThrow(new DBusExecutionException("dbus exception"))
                .when(bluezDevice)
                .connect();

        // When
        peripheral.connect();

        // Then
        verify(internalCallback).connectFailed(peripheral);
        assertEquals(STATE_DISCONNECTED, peripheral.getState());
    }

    @Test
    void Given_connecting_a_peripheral_when_connected_signal_comes_in_then_a_connected_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();

        // Then
        verify(internalCallback).connected(peripheral);
        assertEquals(STATE_CONNECTED, peripheral.getState());
    }

    @Test
    void Given_a_connected_peripheral_when_disconnecting_then_disconnect_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();

        // When
        peripheral.disconnectBluezDevice();

        // Then
        verify(bluezDevice).disconnect();
        assertEquals(STATE_DISCONNECTING, peripheral.getState());
    }

    @Test
    void Given_a_connected_peripheral_when_disconnected_signal_comes_in_then_disconnect_is_sent() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();

        // When
        peripheral.disconnectBluezDevice();
        Thread.sleep(10);
        peripheral.handleSignal(getPropertiesChangedSignalDisconnected());

        // Then
        verify(internalCallback).disconnected(peripheral);
        assertEquals(STATE_DISCONNECTED, peripheral.getState());
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_then_a_read_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(bluezGattCharacteristic).readValue(anyMap());
    }

    @Test
    void Given_a_connected_peripheral_when_a_characteristic_is_read_then_onCharacteristicUpdate_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        peripheral.services.add(characteristic.service);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        when(bluezGattCharacteristic.getDbusPath()).thenReturn("/org/bluez/hci0/dev_C0_26_DF_01_F2_72/service0014/char0015");
        when(bluezGattCharacteristic.getUuid()).thenReturn(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);
        byte[] value = new byte[]{0x01, 0x02};
        peripheral.handleSignal(getPropertiesChangedSignalCharacteristicUpdate(bluezGattCharacteristic.getDbusPath(), characteristic, value));
        Thread.sleep(50);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral, value, characteristic, GATT_SUCCESS);
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_twice_then_a_read_is_done_twice() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.readCharacteristic(characteristic);
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(bluezGattCharacteristic, times(2)).readValue(anyMap());
    }

    @Test
    void Given_a_disconnected_peripheral_when_readCharacteristic_is_called_then_no_read_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);
        when(bluezGattCharacteristic.getDbusPath()).thenReturn("/org/bluez/hci0/characteristic/" + BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(bluezGattCharacteristic, never()).readValue(anyMap());
    }

    @Test
    void Given_a_connected_peripheral_and_not_readable_characteristic_when_readCharacteristic_is_called_then_no_read_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_NOTIFY);
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);
        when(bluezGattCharacteristic.getDbusPath()).thenReturn("/org/bluez/hci0/characteristic/" + BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(bluezGattCharacteristic, never()).readValue(anyMap());
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_with_not_existing_characteristic_then_a_read_is_not_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(bluezGattCharacteristic, never()).readValue(anyMap());
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_and_BluezFailedException_occurs_then_onCharacteristicUpdate_is_called_with_GATT_ERROR() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        when(bluezGattCharacteristic.readValue(anyMap())).thenThrow(BluezFailedException.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral,new byte[0], characteristic, GATT_ERROR);
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_and_BluezNotPermittedException_occurs_then_onCharacteristicUpdate_is_called_with_GATT_READ_NOT_PERMITTED() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        when(bluezGattCharacteristic.readValue(anyMap())).thenThrow(BluezNotPermittedException.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral,new byte[0], characteristic, GATT_READ_NOT_PERMITTED);
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_and_BluezNotAuthorizedException_occurs_then_onCharacteristicUpdate_is_called_with_GATT_INSUFFICIENT_AUTHENTICATION() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        when(bluezGattCharacteristic.readValue(anyMap())).thenThrow(BluezNotAuthorizedException.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral,new byte[0], characteristic, GATT_INSUFFICIENT_AUTHENTICATION);
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_and_BluezNotSupportedException_occurs_then_onCharacteristicUpdate_is_called_with_GATT_REQUEST_NOT_SUPPORTED() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        when(bluezGattCharacteristic.readValue(anyMap())).thenThrow(BluezNotSupportedException.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral,new byte[0], characteristic, GATT_REQUEST_NOT_SUPPORTED);
    }

    @Test
    void Given_a_connected_peripheral_when_readCharacteristic_is_called_and_DBusExecutionException_occurs_then_onCharacteristicUpdate_is_called_with_GATT_ERROR() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        when(bluezGattCharacteristic.readValue(anyMap())).thenThrow(DBusExecutionException.class);

        // When
        peripheral.readCharacteristic(characteristic);
        Thread.sleep(10);

        // Then
        verify(peripheralCallback).onCharacteristicUpdate(peripheral,new byte[0], characteristic, GATT_ERROR);
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_with_WRITE_TYPE_DEFAULT_is_called_then_a_write_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        byte[] value = new byte[]{0x01,0x02,0x03};
        peripheral.writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT);
        Thread.sleep(10);

        // Then
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(bluezGattCharacteristic).writeValue(valueCaptor.capture(),mapCaptor.capture());
        assertEquals("request", mapCaptor.getValue().get("type"));
        assertTrue(Arrays.equals(value, valueCaptor.getValue()));
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_with_WRITE_TYPE_NO_RESPONSE_is_called_then_a_write_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE_NO_RESPONSE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        byte[] value = new byte[]{0x01,0x02,0x03};
        peripheral.writeCharacteristic(characteristic, value, WRITE_TYPE_NO_RESPONSE);

        // Then
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(bluezGattCharacteristic, timeout(50)).writeValue(valueCaptor.capture(),mapCaptor.capture());
        assertEquals("command", mapCaptor.getValue().get("type"));
        assertTrue(Arrays.equals(value, valueCaptor.getValue()));
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_is_called_with_not_existing_characteristic_then_a_write_is_not_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{0x01,0x02,0x03}, WRITE_TYPE_DEFAULT);

        // Then
        verify(bluezGattCharacteristic, timeout(50).times(0)).writeValue(any(), anyMap());
    }

    @Test
    void Given_a_connected_peripheral_and_not_writable_characteristic_when_writeCharacteristic_is_called_then_no_write_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_NOTIFY);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{0x01,0x02,0x03}, WRITE_TYPE_DEFAULT);

        // Then
        verify(bluezGattCharacteristic, timeout(50).times(0)).writeValue(any(), anyMap());
    }

    @Test
    void Given_a_connected_peripheral_and_writable_characteristic_when_writeCharacteristic_is_called_incompatible_writeType_then_no_write_is_done() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE_NO_RESPONSE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.writeCharacteristic(characteristic, new byte[]{0x01,0x02,0x03}, WRITE_TYPE_DEFAULT);

        // Then
        verify(bluezGattCharacteristic, timeout(50).times(0)).writeValue(any(), anyMap());
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_is_called_and_BluezNotPermittedException_occurs_then_onCharacteristicWrite_is_called_with_GATT_WRITE_NOT_PERMITTED() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        doThrow(new BluezNotPermittedException("not permitted"))
                .when(bluezGattCharacteristic)
                .writeValue(any(), anyMap());

        // When
        byte[] value = new byte[]{0x01,0x02,0x03};
        peripheral.writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT);

        // Then
        ArgumentCaptor<BluetoothPeripheral> peripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> characteristicCaptor = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(peripheralCallback, timeout(50)).onCharacteristicWrite(peripheralCaptor.capture(),valueCaptor.capture(), characteristicCaptor.capture(), statusCaptor.capture());
        assertEquals(GATT_WRITE_NOT_PERMITTED, statusCaptor.getValue());
        assertTrue(Arrays.equals(value, valueCaptor.getValue()));
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_is_called_and_BluezNotAuthorizedException_occurs_then_onCharacteristicWrite_is_called_with_GATT_INSUFFICIENT_AUTHENTICATION() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        doThrow(new BluezNotAuthorizedException("not authorized"))
                .when(bluezGattCharacteristic)
                .writeValue(any(), anyMap());

        // When
        byte[] value = new byte[]{0x01,0x02,0x03};
        peripheral.writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT);

        // Then
        ArgumentCaptor<BluetoothPeripheral> peripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> characteristicCaptor = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(peripheralCallback, timeout(50)).onCharacteristicWrite(peripheralCaptor.capture(),valueCaptor.capture(), characteristicCaptor.capture(), statusCaptor.capture());
        assertEquals(GATT_INSUFFICIENT_AUTHENTICATION, statusCaptor.getValue());
        assertTrue(Arrays.equals(value, valueCaptor.getValue()));
    }

    @Test
    void Given_a_connected_peripheral_when_writeCharacteristic_is_called_and_BluezFailedException_occurs_then_onCharacteristicWrite_is_called_with_GATT_ERROR() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_WRITE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        doThrow(new BluezFailedException("failed"))
                .when(bluezGattCharacteristic)
                .writeValue(any(), anyMap());

        // When
        byte[] value = new byte[]{0x01,0x02,0x03};
        peripheral.writeCharacteristic(characteristic, value, WRITE_TYPE_DEFAULT);

        // Then
        ArgumentCaptor<BluetoothPeripheral> peripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BluetoothGattCharacteristic> characteristicCaptor = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(peripheralCallback, timeout(50)).onCharacteristicWrite(peripheralCaptor.capture(),valueCaptor.capture(), characteristicCaptor.capture(), statusCaptor.capture());
        assertEquals(GATT_ERROR, statusCaptor.getValue());
        assertTrue(Arrays.equals(value, valueCaptor.getValue()));
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_on_characteristic_that_supports_PROPERTY_INDICATE_then_startNotify_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_INDICATE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.setNotify(characteristic, true);

        // Then
        verify(bluezGattCharacteristic, timeout(50)).startNotify();
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_on_characteristic_that_supports_PROPERTY_NOTIFY_then_startNotify_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_NOTIFY);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.setNotify(characteristic, true);

        // Then
        verify(bluezGattCharacteristic, timeout(50)).startNotify();
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_with_false_then_stopNotify_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_NOTIFY);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.setNotify(characteristic, false);

        // Then
        verify(bluezGattCharacteristic, timeout(50)).stopNotify();
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_on_characteristic_that_does_not_support_notifying_then_startNotify_is_not_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.setNotify(characteristic, true);

        // Then
        verify(bluezGattCharacteristic, timeout(50).times(0)).startNotify();
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_on_characteristic_with_not_existing_characteristic_then_startNotify_is_not_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_READ);
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);

        // When
        peripheral.setNotify(characteristic, true);

        // Then
        verify(bluezGattCharacteristic, timeout(50).times(0)).startNotify();
    }

    @Test
    void Given_a_connected_peripheral_and_setNotify_has_been_called_when_the_notifyingSignal_comes_in_then_onNotificationStateUpdate_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_NOTIFY);
        peripheral.services.add(characteristic.getService());
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        when(bluezGattCharacteristic.getDbusPath()).thenReturn("/org/bluez/hci0/dev_C0_26_DF_01_F2_72/service0014/char0015");
        when(bluezGattCharacteristic.getUuid()).thenReturn(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);

        // When
        peripheral.setNotify(characteristic, true);
        Thread.sleep(50);
        peripheral.handleSignal(getPropertiesChangedSignalCharacteristicNotifying("/org/bluez/hci0/dev_C0_26_DF_01_F2_72/service0014/char0015", true));

        // Then
        verify(peripheralCallback, timeout(50)).onNotificationStateUpdate(peripheral, characteristic, GATT_SUCCESS);
    }

    @Test
    void Given_a_connected_peripheral_when_setNotify_is_called_and_Bluez_occurs_then_onNotificationState_is_called_with_GATT_ERROR() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluetoothGattCharacteristic characteristic = getBluetoothGattCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID, PROPERTY_INDICATE);
        BluezGattCharacteristic bluezGattCharacteristic = getBluezGattCharacteristic();
        peripheral.characteristicMap.put(bluezGattCharacteristic.getDbusPath(), bluezGattCharacteristic);
        doThrow(new BluezFailedException("failed"))
                .when(bluezGattCharacteristic)
                .startNotify();

        // When
        peripheral.setNotify(characteristic, true);

        // Then
        ArgumentCaptor<BluetoothPeripheral> peripheralCaptor = ArgumentCaptor.forClass(BluetoothPeripheral.class);
        ArgumentCaptor<BluetoothGattCharacteristic> characteristicCaptor = ArgumentCaptor.forClass(BluetoothGattCharacteristic.class);
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(peripheralCallback, timeout(50)).onNotificationStateUpdate(peripheralCaptor.capture(), characteristicCaptor.capture(), statusCaptor.capture());
        assertEquals(GATT_ERROR, statusCaptor.getValue());
    }

    @Test
    void Given_a_connected_peripheral_when_servicesResolved_comes_in_then_onServices_is_called() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();

        // When
        peripheral.handleSignal(getPropertiesChangedSignalServicesResolved());

        // Then
        verify(peripheralCallback, timeout(50)).onServicesDiscovered(peripheral);
    }

    @Test
    void Given_a_connected_peripheral_when_servicesResolved_comes_in_then_the_gatt_tree_is_build() throws DBusException, InterruptedException {
        // Given
        BluetoothPeripheral peripheral = getConnectedPeripheral();
        BluezDevice bluezDevice = mock(BluezDevice.class);
        peripheral.setDevice(bluezDevice);
        BluezGattService service1 = mock(BluezGattService.class);
        when(service1.getUuid()).thenReturn(BLP_SERVICE_UUID.toString());
        when(service1.getDbusPath()).thenReturn("a");
        BluezGattService service2 = mock(BluezGattService.class);
        when(service2.getUuid()).thenReturn(HTS_SERVICE_UUID.toString());
        when(service2.getDbusPath()).thenReturn("b");
        List<BluezGattService> serviceList = new ArrayList<>();
        serviceList.add(service1);
        serviceList.add(service2);

        BluezGattCharacteristic characteristic1 = mock(BluezGattCharacteristic.class);
        when(characteristic1.getUuid()).thenReturn(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        when(characteristic1.getDbusPath()).thenReturn("aa");
        List<String> flags1 = new ArrayList<>();
        flags1.add("read");
        flags1.add("notify");
        when(characteristic1.getFlags()).thenReturn(flags1);
        BluezGattCharacteristic characteristic2 = mock(BluezGattCharacteristic.class);
        when(characteristic2.getUuid()).thenReturn(TEMPERATURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        when(characteristic2.getDbusPath()).thenReturn("bb");

        BluezGattDescriptor descriptor = mock(BluezGattDescriptor.class);
        when(descriptor.getUuid()).thenReturn(CCC_DESCRIPTOR_UUID);
        when(descriptor.getDbusPath()).thenReturn("ccc");

        when(bluezDevice.getGattServices()).thenReturn(serviceList);
        when(service1.getGattCharacteristics()).thenReturn(Collections.singletonList(characteristic1));
        when(service2.getGattCharacteristics()).thenReturn(Collections.singletonList(characteristic2));
        when(characteristic1.getGattDescriptors()).thenReturn(Collections.singletonList(descriptor));

        // When
        peripheral.handleSignal(getPropertiesChangedSignalServicesResolved());

        // Then
        verify(peripheralCallback, timeout(50)).onServicesDiscovered(peripheral);
        assertEquals(2, peripheral.services.size());
        assertNotNull(peripheral.getService(BLP_SERVICE_UUID));
        assertNotNull(peripheral.getService(HTS_SERVICE_UUID));

        BluetoothGattCharacteristic measurementCharacteristic = peripheral.getCharacteristic(BLP_SERVICE_UUID, BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID);
        assertNotNull(measurementCharacteristic);
        assertTrue(measurementCharacteristic.supportsReading());
        assertTrue(measurementCharacteristic.supportsNotifying());
    }

    @NotNull
    private BluezGattCharacteristic getBluezGattCharacteristic() {
        BluezGattCharacteristic bluezGattCharacteristic = mock(BluezGattCharacteristic.class);
        BluezGattService bluezGattService = mock(BluezGattService.class);
        when(bluezGattCharacteristic.getDbusPath()).thenReturn("/org/bluez/hci0/characteristic/" + BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        when(bluezGattCharacteristic.getUuid()).thenReturn(BLOOD_PRESSURE_MEASUREMENT_CHARACTERISTIC_UUID.toString());
        when(bluezGattCharacteristic.getService()).thenReturn(bluezGattService);
        when(bluezGattService.getUuid()).thenReturn(BLP_SERVICE_UUID.toString());
        return bluezGattCharacteristic;
    }

    @NotNull
    private BluetoothGattCharacteristic getBluetoothGattCharacteristic(UUID serviceUUID, UUID characteristicUUID, int properties) {
        BluetoothGattService service = new BluetoothGattService(serviceUUID);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(characteristicUUID, properties,0 );
        characteristic.setService(service);
        service.addCharacteristic(characteristic);
        return characteristic;
    }


    @NotNull
    private BluetoothPeripheral getConnectedPeripheral() throws InterruptedException, DBusException {
        // Given
        BluetoothPeripheral peripheral = getPeripheral();

        // When
        peripheral.connect();

        Thread.sleep(10);
        peripheral.handleSignal(getPropertiesChangedSignalConnected());
        return peripheral;
    }

    @NotNull
    private BluetoothPeripheral getPeripheral() {
        BluezSignalHandler.createInstance(dBusConnection);
        return new BluetoothPeripheral(central, bluezDevice, DUMMY_PERIPHERAL_NAME_BLP, DUMMY_MAC_ADDRESS_BLP, internalCallback, peripheralCallback, callbackHandler);
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangedSignalConnected() throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_CONNECTED, new Variant<>(true));
        return new Properties.PropertiesChanged("/org/bluez/hci0/dev_C0_26_DF_01_F2_72", BLUEZ_DEVICE_INTERFACE, propertiesChanged,new ArrayList() );
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangedSignalDisconnected() throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_CONNECTED, new Variant<>(false));
        return new Properties.PropertiesChanged("/org/bluez/hci0/dev_C0_26_DF_01_F2_72", BLUEZ_DEVICE_INTERFACE, propertiesChanged,new ArrayList() );
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangedSignalServicesResolved() throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_SERVICES_RESOLVED, new Variant<>(true));
        return new Properties.PropertiesChanged("/org/bluez/hci0/dev_C0_26_DF_01_F2_72", BLUEZ_DEVICE_INTERFACE, propertiesChanged,new ArrayList() );
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangedSignalCharacteristicUpdate(String path, BluetoothGattCharacteristic characteristic, byte[] value) throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_VALUE, new Variant<>(value, "ay"));
        return new Properties.PropertiesChanged(path, BLUEZ_CHARACTERISTIC_INTERFACE, propertiesChanged,new ArrayList() );
    }

    @NotNull
    private Properties.PropertiesChanged getPropertiesChangedSignalCharacteristicNotifying(String path, boolean value) throws DBusException {
        Map<String, Variant<?>> propertiesChanged = new HashMap<>();
        propertiesChanged.put(PROPERTY_NOTIFYING, new Variant<>(value));
        return new Properties.PropertiesChanged(path, BLUEZ_CHARACTERISTIC_INTERFACE, propertiesChanged,new ArrayList<String>() );
    }
}