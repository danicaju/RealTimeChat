/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package proyectopersonal;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LT {

    private static String cadena = "";

    private char[] llegirln() {
        String res = "";
        if (LT.cadena.length() == 0) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(System.in, "ISO-8859-1"));
                res = br.readLine();
            } catch (Exception e) {
                e.printStackTrace();
            }
            LT.cadena = res;
        }

        res = LT.cadena;

        return res.toCharArray();
    }

    public Integer llegirEnter() {
        Integer res = 0;
        try {
            char[] pal = llegirln();
            res = Integer.valueOf(new String(pal));
        } catch (NumberFormatException e) {
            res = null;
        }
        LT.cadena = "";
        return res;
    }

    public Double llegirReal() {
        Double res = 0.0;
        try {
            char[] pal = llegirln();
            res = Double.valueOf(new String(pal));
        } catch (NumberFormatException e) {
            res = null;
        }
        LT.cadena = "";
        return res;
    }

    public char[] llegirLinia() {
        char[] res = llegirln();

        LT.cadena = "";
        return res;
    }

    public Character llegirCaracter() {
        Character res;
        char[] par = llegirln();

        String s = new String(par);

        if (s.length() == 0) {
            res = null;
        } else {
            res = s.charAt(0);
            LT.cadena = s.substring(1);
        }
        return res;
    }

    public void clear() {
        LT.cadena = "";
    }
}
