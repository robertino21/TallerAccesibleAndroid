package com.example.talleraccesible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PantallaAjustes(vm: AppViewModel) {
    var nombrePlantilla by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Configuración", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        // TARJETA DE TEMA
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Tema de la aplicación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar tema oscuro")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = vm.forzarOscuro == true, onCheckedChange = { vm.forzarOscuro = it })
                }
            }
        }

        // TARJETA DE PLANTILLAS
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Plantillas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                
                OutlinedTextField(
                    value = nombrePlantilla, onValueChange = { nombrePlantilla = it },
                    label = { Text("Nombre de la nueva plantilla") }, modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = { vm.guardarPlantilla(nombrePlantilla); nombrePlantilla = "" }, 
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar estilo actual como plantilla") }

                // Solo se muestra si hay plantillas guardadas
                if (vm.plantillas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Plantillas guardadas:", fontWeight = FontWeight.Medium)
                    // Acá podés usar tu función CampoLista apuntando a vm.plantillas
                    Text("Tienes ${vm.plantillas.size} plantillas en tu lista.", fontSize = 14.sp)
                }
            }
        }
    }
}