package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class RegistroActivity extends AppCompatActivity {

    private static final String PATRON_NOMBRE = "\\p{L}+([ '\\-]\\p{L}+)*";
    private static final String PATRON_CIP = "\\d{5,9}";
    private static final String PATRON_DNI = "\\d{8}";
    private static final String PATRON_CODIGO_INFRACCION = "[A-Z]{1,3}-?\\d{1,3}";

    private ScrollView scroll;
    private TextInputLayout layoutGrado, layoutCip, layoutDni, layoutApellidos, layoutNombres,
            layoutCodigoInfraccion, layoutFecha, layoutNumeroNota, layoutHora;
    private TextInputEditText etGrado, etCip, etDni, etApellidos, etNombres, etCodigoInfraccion,
            etFecha, etNumeroNota, etHora;

    private TextInputLayout primerError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Sesion.esAdministrador()) {
            startActivity(new Intent(this, AccesoActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registro);
        scroll = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        layoutGrado = findViewById(R.id.layoutGrado);
        layoutCip = findViewById(R.id.layoutCip);
        layoutDni = findViewById(R.id.layoutDni);
        layoutApellidos = findViewById(R.id.layoutApellidos);
        layoutNombres = findViewById(R.id.layoutNombres);
        layoutCodigoInfraccion = findViewById(R.id.layoutCodigoInfraccion);
        layoutFecha = findViewById(R.id.layoutFecha);
        layoutNumeroNota = findViewById(R.id.layoutNumeroNota);
        layoutHora = findViewById(R.id.layoutHora);
        etGrado = findViewById(R.id.etGrado);
        etCip = findViewById(R.id.etCip);
        etDni = findViewById(R.id.etDni);
        etApellidos = findViewById(R.id.etApellidos);
        etNombres = findViewById(R.id.etNombres);
        etCodigoInfraccion = findViewById(R.id.etCodigoInfraccion);
        etFecha = findViewById(R.id.etFecha);
        etNumeroNota = findViewById(R.id.etNumeroNota);
        etHora = findViewById(R.id.etHora);
        MaterialButton btnGuardar = findViewById(R.id.btnGuardar);

        limpiarErrorAlEscribir(etGrado, layoutGrado);
        limpiarErrorAlEscribir(etCip, layoutCip);
        limpiarErrorAlEscribir(etDni, layoutDni);
        limpiarErrorAlEscribir(etApellidos, layoutApellidos);
        limpiarErrorAlEscribir(etNombres, layoutNombres);
        limpiarErrorAlEscribir(etCodigoInfraccion, layoutCodigoInfraccion);
        limpiarErrorAlEscribir(etFecha, layoutFecha);
        limpiarErrorAlEscribir(etNumeroNota, layoutNumeroNota);

        etFecha.setOnClickListener(v -> elegirFecha());
        layoutFecha.setEndIconOnClickListener(v -> elegirFecha());
        etHora.setOnClickListener(v -> elegirHora());
        layoutHora.setEndIconOnClickListener(v -> elegirHora());
        btnGuardar.setOnClickListener(v -> guardar());
    }

    private void elegirFecha() {
        CalendarConstraints restricciones = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();
        MaterialDatePicker<Long> selector = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.falta_fecha)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(restricciones)
                .build();
        selector.addOnPositiveButtonClickListener(milisegundos -> {
            SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "PE"));
            formato.setTimeZone(TimeZone.getTimeZone("UTC"));
            etFecha.setText(formato.format(new Date(milisegundos)));
        });
        selector.show(getSupportFragmentManager(), "fecha");
    }

    private void elegirHora() {
        MaterialTimePicker selector = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(8)
                .setMinute(0)
                .setTitleText(R.string.falta_hora)
                .build();
        selector.addOnPositiveButtonClickListener(v -> etHora.setText(
                String.format(Locale.ROOT, "%02d:%02d", selector.getHour(), selector.getMinute())));
        selector.show(getSupportFragmentManager(), "hora");
    }

    private void guardar() {
        if (!validarFormulario()) {
            mostrarPrimerError();
            return;
        }
        // Aquí se enviarían los datos a un servidor o base de datos.
        String grado = texto(etGrado).trim().toUpperCase(Locale.ROOT);
        String apellidos = texto(etApellidos).trim();
        Toast.makeText(this, getString(R.string.falta_guardada, grado, apellidos),
                Toast.LENGTH_LONG).show();
    }

    private boolean validarFormulario() {
        primerError = null;
        boolean valido = true;

        if (texto(etGrado).trim().isEmpty()) {
            valido = marcarError(layoutGrado, R.string.error_grado);
        }
        if (!texto(etCip).trim().matches(PATRON_CIP)) {
            valido = marcarError(layoutCip, R.string.error_cip);
        }
        String dni = texto(etDni).trim();
        if (!dni.isEmpty() && !dni.matches(PATRON_DNI)) {
            valido = marcarError(layoutDni, R.string.error_dni);
        }
        if (!texto(etApellidos).trim().matches(PATRON_NOMBRE)) {
            valido = marcarError(layoutApellidos, R.string.error_apellidos);
        }
        if (!texto(etNombres).trim().matches(PATRON_NOMBRE)) {
            valido = marcarError(layoutNombres, R.string.error_nombres);
        }
        String codigo = texto(etCodigoInfraccion).trim().toUpperCase(Locale.ROOT);
        if (!codigo.isEmpty() && !codigo.matches(PATRON_CODIGO_INFRACCION)) {
            valido = marcarError(layoutCodigoInfraccion, R.string.error_codigo_infraccion);
        }
        if (texto(etFecha).trim().isEmpty()) {
            valido = marcarError(layoutFecha, R.string.error_fecha);
        }
        if (texto(etNumeroNota).trim().isEmpty()) {
            valido = marcarError(layoutNumeroNota, R.string.error_numero_nota);
        }
        return valido;
    }

    private boolean marcarError(TextInputLayout layout, int mensaje) {
        layout.setError(getString(mensaje));
        if (primerError == null) {
            primerError = layout;
        }
        return false;
    }

    private void mostrarPrimerError() {
        if (primerError == null) {
            return;
        }
        scroll.smoothScrollTo(0, primerError.getTop());
        EditText campo = primerError.getEditText();
        if (campo != null && campo.isFocusable()) {
            campo.requestFocus();
        }
    }

    private static String texto(TextInputEditText campo) {
        Editable editable = campo.getText();
        return editable == null ? "" : editable.toString();
    }

    private static void limpiarErrorAlEscribir(TextInputEditText campo, TextInputLayout layout) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
