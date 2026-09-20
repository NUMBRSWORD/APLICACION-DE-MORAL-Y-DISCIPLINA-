package com.hidalgoferrai.myapplication;

public final class Sesion {

    public enum Rol {ADMINISTRADOR, USUARIO}

    private static Rol rol;

    private Sesion() {
    }

    public static Rol getRol() {
        return rol;
    }

    public static void setRol(Rol nuevoRol) {
        rol = nuevoRol;
    }

    public static boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    public static void cerrar() {
        rol = null;
    }
}
