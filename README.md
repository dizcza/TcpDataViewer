# TCP client sensor data Android viewer

F.i., ESP32 or Pi Pico board serves as a TCP server, Android - as a TCP client. The server streams binary data - magnetic field - and Android displays it.

To be as simple as possible, it's assumed that the input binary stream is a 1-dimensional stream of raw data points (see "Supported binary data types" below). The value of received data points is displayed on the Y axis, and the point counter - on the X axis.

## Menu options

### Connection

Specify TCP server IP address and port number.

### Data protocol

Specify data type and endianness (big/little).

#### Supported binary data types

* `int8_t`
* `int16_t`
* `int32_t`
* `int64_t`
* `float`
* `double`

### Plot settings

Specify plot chart max size (keep last `N` points) and update (refresh) period in ms.

## Saved charts

Click `Save` button to save the current chart in a local storage. To browse stored charts, navigate to *Show saved charts* menu option.

## Screenshots

![screenshot](./screenshots/screenshot.jpg)
