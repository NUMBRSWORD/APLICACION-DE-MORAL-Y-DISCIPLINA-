package com.hidalgoferrai.myapplication;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AvisosFirebaseServiceTest {
    @Test public void aceptaSoloTiposDeAvisoConocidos() {
        assertEquals(R.string.aviso_caso_nuevo,
                AvisosFirebaseService.textoParaTipo("caso_nuevo"));
        assertEquals(R.string.aviso_plazo_descargo,
                AvisosFirebaseService.textoParaTipo("plazo_descargo"));
        assertEquals(R.string.aviso_pasos_pendientes,
                AvisosFirebaseService.textoParaTipo("pasos_pendientes"));
        assertEquals(R.string.aviso_documento_recibido,
                AvisosFirebaseService.textoParaTipo("documento_recibido"));
        assertEquals(0, AvisosFirebaseService.textoParaTipo("mensaje_libre"));
        assertEquals(0, AvisosFirebaseService.textoParaTipo(null));
        assertEquals(R.string.aviso_prueba, AvisosFirebaseService.textoParaTipo("prueba"));
    }
}
