package app.familygem;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;

import java.util.ArrayList;
import java.util.List;

public class NuovoParente extends DialogFragment {

	Person perno;
	private boolean parenteNuovo;
	private Fragment frammento;
	private Spinner spinner;
	private List<VoceFamiglia> voci = new ArrayList<>();
	private int relazione;

	NuovoParente( Person perno, boolean nuovo, Fragment frammento ) {
		this.perno = perno;
		parenteNuovo = nuovo;
		this.frammento = frammento;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		//builder.setTitle( nuovo ? R.string.new_relative : R.string.link_person );
		View vista = requireActivity().getLayoutInflater().inflate( R.layout.nuovo_parente, null );
		// Spinner per segliere la famiglia
		spinner = vista.findViewById(R.id.nuovoparente_famiglie);
		ArrayAdapter<VoceFamiglia> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		((View)spinner.getParent()).setVisibility( View.GONE );

		RadioButton ruolo1 = vista.findViewById( R.id.nuovoparente_1 );
		ruolo1.setOnCheckedChangeListener( (r,selected) -> {
			if(selected) popolaSpinner(1);
		});
		RadioButton ruolo2 = vista.findViewById( R.id.nuovoparente_2 );
		ruolo2.setOnCheckedChangeListener( (r,selected) -> {
			if(selected) popolaSpinner(2);
		});
		RadioButton ruolo3 = vista.findViewById( R.id.nuovoparente_3 );
		ruolo3.setOnCheckedChangeListener( (r,selected) -> {
			if(selected) popolaSpinner(3);
		});
		RadioButton ruolo4 = vista.findViewById( R.id.nuovoparente_4 );
		ruolo4.setOnCheckedChangeListener( (r,selected) -> {
			if(selected) popolaSpinner(4);
		});

		builder.setView(vista).setPositiveButton( android.R.string.ok, (dialog,id) -> {
			String idFamiglia = null;
			VoceFamiglia voceFamiglia = (VoceFamiglia) spinner.getSelectedItem();
			if( voceFamiglia.famiglia != null )
				idFamiglia = voceFamiglia.famiglia.getId();
			else if( voceFamiglia.genitore != null ) // Uso 'idFamiglia' per veicolare l'id del genitore
				idFamiglia = "NUOVA_FAMIGLIA_DI" + voceFamiglia.genitore.getId();
			if( parenteNuovo ) {
				Intent intento = new Intent( getContext(), EditaIndividuo.class );
				intento.putExtra( "idIndividuo", perno.getId() );
				intento.putExtra( "relazione", relazione );
				intento.putExtra( "idFamiglia", idFamiglia );
				startActivity( intento );
			} else {
				Intent intento = new Intent( getContext(), Principe.class );
				intento.putExtra( "anagrafeScegliParente", true );
				intento.putExtra( "relazione", relazione );
				intento.putExtra( "idFamiglia", idFamiglia );
				if( frammento != null )
					frammento.startActivityForResult( intento, 1401 );
				else
					getActivity().startActivityForResult( intento,1401 );  // ???
			}
		}).setNeutralButton( R.string.cancel, null );
		return builder.create();
	}

	private void popolaSpinner(int relazione) {
		this.relazione = relazione;
		voci.clear();
		if( relazione == 1 ) { // Genitore
			for( Family fam : perno.getParentFamilies(Globale.gc) ) {
				voci.add( new VoceFamiglia(getContext(),fam) );
			}
			voci.add( new VoceFamiglia(getContext()) );
		} else if( relazione == 2 ) { // Fratello
			for( Family fam : perno.getParentFamilies(Globale.gc) ) {
				voci.add( new VoceFamiglia(getContext(),fam) );
				for( Person padre : fam.getHusbands(Globale.gc) ) {
					for( Family fam2 : padre.getSpouseFamilies(Globale.gc) )
						if( !fam2.equals(fam) )
							voci.add( new VoceFamiglia(getContext(),fam2) );
					voci.add( new VoceFamiglia(getContext(),padre) );
				}
				for( Person madre : fam.getWives(Globale.gc) ) {
					for( Family fam2 : madre.getSpouseFamilies(Globale.gc) )
						if( !fam2.equals(fam) )
							voci.add( new VoceFamiglia(getContext(),fam2) );
					voci.add( new VoceFamiglia(getContext(),madre) );
				}
			}
			voci.add( new VoceFamiglia(getContext()) );
		} else if( relazione == 3 || relazione == 4 ) { // Coniuge / Figlio
			for( Family fam : perno.getSpouseFamilies(Globale.gc) ) {
				voci.add( new VoceFamiglia(getContext(),fam) );
			}
			voci.add( new VoceFamiglia(getContext(),perno) );
		}
		ArrayAdapter<VoceFamiglia> adapter = (ArrayAdapter) spinner.getAdapter();
		adapter.clear();
		adapter.addAll(voci);
		((View)spinner.getParent()).setVisibility( View.VISIBLE );
	}

	// Classe per le voci degli elenchi di famiglie nei dialoghi "A quale famiglia vuoi aggiungere...?"
	static class VoceFamiglia {
		Context contesto;
		Family famiglia;
		Person genitore;

		VoceFamiglia(Context contesto, Family famiglia) {
			this.contesto = contesto;
			this.famiglia = famiglia;
		}

		VoceFamiglia(Context contesto, Person genitore) {
			this.contesto = contesto;
			this.genitore = genitore;
		}

		VoceFamiglia(Context contesto) {
			this.contesto = contesto;
		}

		@Override
		public String toString() {
			if( famiglia != null)
				return U.testoFamiglia(contesto, Globale.gc, famiglia, true);
			else if( genitore != null )
				return contesto.getString(R.string.new_family_of, U.epiteto(genitore));
			else
				return contesto.getString(R.string.new_family);
		}
	}
}
